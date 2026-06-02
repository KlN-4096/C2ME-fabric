package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.RangeChoiceNode;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.BoundaryUse;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.RegionUses;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.VisibleUses;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import net.minecraft.util.math.Spline;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;

import java.util.IdentityHashMap;
import java.util.List;

final class CacheUseAnalyzer {

    private CacheUseAnalyzer() {
    }

    static CacheUseAnalysis analyze(AstNode root) {
        IdentityHashMap<AstNode, BoundaryUse> boundaryUses = collectBoundaryUses(root);
        RegionUses regionUses = collectRegionUses(root, boundaryUses);
        return new CacheUseAnalysis(boundaryUses, regionUses);
    }

    private static IdentityHashMap<AstNode, BoundaryUse> collectBoundaryUses(AstNode node) {
        IdentityHashMap<AstNode, BoundaryUse> boundaryUses = new IdentityHashMap<>();
        collectBoundaryUses(node, false, boundaryUses);
        return boundaryUses;
    }

    /*
     * Tracks whether every DAG node is seen from outside a strict cache boundary, from inside
     * one, or both. A node that is shared across that boundary cannot be rebuilt safely because
     * inserting another cache there would change which existing cache owns the computation.
     */
    private static void collectBoundaryUses(AstNode node, boolean insideBoundary, IdentityHashMap<AstNode, BoundaryUse> boundaryUses) {
        BoundaryUse boundaryUse = boundaryUses.computeIfAbsent(node, unused -> new BoundaryUse());
        if (!boundaryUse.mark(insideBoundary)) {
            return;
        }

        boolean childInsideBoundary = insideBoundary || node instanceof CacheLikeNode cacheLikeNode && cacheLikeNode.blocksCacheInsertion();
        for (AstNode child : node.getChildren()) {
            collectBoundaryUses(child, childInsideBoundary, boundaryUses);
        }
    }

    /*
     * Builds the complete use model for placement. Visible uses are collected first to choose
     * top-level Cache2D roots, then rebuilt with those roots treated as opaque so nested choices
     * are based on miss-body uses instead of the always-visible path.
     */
    private static RegionUses collectRegionUses(AstNode root, IdentityHashMap<AstNode, BoundaryUse> boundaryUses) {
        RegionUses regionUses = new RegionUses(boundaryUses);
        IdentityHashMap<AstNode, Boolean> selected = new IdentityHashMap<>();
        regionUses.replaceVisibleUses(collectVisibleUses(root, selected));
        CacheCandidateSelector.selectVisibleCache2Ds(root, regionUses, selected);
        refreshSelectedUses(root, regionUses, selected);
        CacheCandidateSelector.selectMissBodyCache2Ds(regionUses, selected);
        refreshSelectedUses(root, regionUses, selected);
        return regionUses;
    }

    private static void refreshSelectedUses(AstNode root, RegionUses regionUses, IdentityHashMap<AstNode, Boolean> selected) {
        regionUses.replaceSelectedCache2DNodes(selected);
        regionUses.replaceVisibleUses(collectVisibleUses(root, selected));
        regionUses.replaceMissBodyUses(collectMissBodyUsesByRoot(selected));
    }

    private static VisibleUses collectVisibleUses(AstNode node, IdentityHashMap<AstNode, Boolean> selectedCache2DNodes) {
        VisibleUses result = new VisibleUses();
        collectVisibleUses(node, selectedCache2DNodes, result);
        return result;
    }

    /*
     * Counts nodes along paths that are visible during normal evaluation. Already-selected Cache2D
     * roots stop traversal here; their children are counted separately as miss-body work because
     * they only run when that cache misses.
     */
    private static void collectVisibleUses(AstNode node, IdentityHashMap<AstNode, Boolean> selectedCache2DNodes, VisibleUses result) {
        result.add(node, 1);
        if (selectedCache2DNodes.containsKey(node)) {
            return;
        }
        collectVisibleChildren(node, selectedCache2DNodes, result);
    }

    private static void collectVisibleChildren(AstNode node, IdentityHashMap<AstNode, Boolean> selectedCache2DNodes, VisibleUses result) {
        if (node instanceof RangeChoiceNode rangeChoiceNode) {
            collectVisibleUses(rangeChoiceNode.getInput(), selectedCache2DNodes, result);
            VisibleUses inRange = collectVisibleUses(rangeChoiceNode.getWhenInRange(), selectedCache2DNodes);
            VisibleUses outOfRange = collectVisibleUses(rangeChoiceNode.getWhenOutOfRange(), selectedCache2DNodes);
            result.addAll(VisibleUses.max(inRange, outOfRange));
            return;
        }
        if (node instanceof SplineAstNode splineAstNode) {
            result.addAll(collectSplineVisibleUses(splineAstNode, splineAstNode.getSpline(), selectedCache2DNodes));
            return;
        }
        for (AstNode child : node.getChildren()) {
            collectVisibleUses(child, selectedCache2DNodes, result);
        }
    }

    private static IdentityHashMap<AstNode, VisibleUses> collectMissBodyUsesByRoot(IdentityHashMap<AstNode, Boolean> selectedCache2DNodes) {
        IdentityHashMap<AstNode, VisibleUses> result = new IdentityHashMap<>();
        for (AstNode selectedRoot : selectedCache2DNodes.keySet()) {
            result.put(selectedRoot, collectMissBodyUses(selectedRoot, selectedCache2DNodes));
        }
        return result;
    }

    private static VisibleUses collectMissBodyUses(AstNode selectedRoot, IdentityHashMap<AstNode, Boolean> selectedCache2DNodes) {
        VisibleUses uses = new VisibleUses();
        collectVisibleChildren(selectedRoot, selectedCache2DNodes, uses);
        return uses;
    }

    private static VisibleUses collectSplineVisibleUses(
            SplineAstNode owner,
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
            IdentityHashMap<AstNode, Boolean> selectedCache2DNodes
    ) {
        VisibleUses result = new VisibleUses();
        if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
            return result;
        }
        AstNode locationFunction = owner.locationAstFor(impl.locationFunction().function().value());
        collectVisibleUses(locationFunction, selectedCache2DNodes, result);
        result.addAll(collectActiveSplineValueVisibleUses(impl.values(), value -> collectSplineVisibleUses(owner, value, selectedCache2DNodes)));
        return result;
    }

    private static VisibleUses collectActiveSplineValueVisibleUses(
            List<Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>> values,
            java.util.function.Function<Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>, VisibleUses> collector
    ) {
        VisibleUses result = new VisibleUses();
        for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : values) {
            result.addAll(collector.apply(value));
        }
        if (values.size() > 2) {
            result.scaleDiv(values.size());
            result.scale(2);
        }
        return result;
    }
}
