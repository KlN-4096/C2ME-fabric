package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.BoundaryUse;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.RegionUses;

import java.util.ArrayList;
import java.util.IdentityHashMap;

final class CacheCandidateSelector {
    private static final int CACHE2D_SINGLE_MISS_USE = 1;

    private CacheCandidateSelector() {
    }

    static void selectVisibleCache2Ds(AstNode root, RegionUses regionUses, IdentityHashMap<AstNode, Boolean> selected) {
        selectVisibleCache2D(root, regionUses, selected);
    }

    private static boolean selectVisibleCache2D(AstNode node, RegionUses regionUses, IdentityHashMap<AstNode, Boolean> selected) {
        if (selected.containsKey(node)) {
            return false;
        }
        int visibleUses = regionUses.visibleUseCount(node);
        if (shouldSelectVisibleCache2D(node, visibleUses, regionUses.boundaryUses)) {
            selected.put(node, Boolean.TRUE);
            return true;
        }
        boolean changed = false;
        for (AstNode child : node.getChildren()) {
            if (selectVisibleCache2D(child, regionUses, selected)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean shouldSelectVisibleCache2D(AstNode node, int visibleUses, IdentityHashMap<AstNode, BoundaryUse> boundaryUses) {
        if (visibleUses <= 0 || DagCseOptimizer.boundaryCrossesStrictCache(node, boundaryUses)) {
            return false;
        }
        int cost = node.cachePlacementCost();
        return isCache2DPlacementCandidate(node, cost);
    }

    static void selectMissBodyCache2Ds(RegionUses regionUses, IdentityHashMap<AstNode, Boolean> selected) {
        for (AstNode root : new ArrayList<>(selected.keySet())) {
            selectMissBodyCache2DChildren(root, root, CACHE2D_SINGLE_MISS_USE, regionUses, selected);
        }
    }

    private static boolean selectMissBodyCache2D(
            AstNode node,
            AstNode covering2DRoot,
            int selectedAncestorUses,
            RegionUses regionUses,
            IdentityHashMap<AstNode, Boolean> selected
    ) {
        int missBodyUses = regionUses.missBodyUseCount(node, covering2DRoot);
        boolean changed = false;
        if (shouldSelectMissBodyCache2D(node, missBodyUses, selectedAncestorUses, regionUses.boundaryUses)) {
            selected.put(node, Boolean.TRUE);
            changed = true;
        }
        int nextAncestorUses = selected.containsKey(node)
                ? Math.max(selectedAncestorUses, missBodyUses)
                : selectedAncestorUses;
        return selectMissBodyCache2DChildren(node, covering2DRoot, nextAncestorUses, regionUses, selected) || changed;
    }

    private static boolean selectMissBodyCache2DChildren(
            AstNode node,
            AstNode covering2DRoot,
            int selectedAncestorUses,
            RegionUses regionUses,
            IdentityHashMap<AstNode, Boolean> selected
    ) {
        boolean changed = false;
        for (AstNode child : node.getChildren()) {
            changed |= selectMissBodyCache2D(child, covering2DRoot, selectedAncestorUses, regionUses, selected);
        }
        return changed;
    }

    private static boolean shouldSelectMissBodyCache2D(
            AstNode node,
            int missBodyUses,
            int selectedAncestorUses,
            IdentityHashMap<AstNode, BoundaryUse> boundaryUses
    ) {
        if (missBodyUses <= selectedAncestorUses || DagCseOptimizer.boundaryCrossesStrictCache(node, boundaryUses)) {
            return false;
        }
        int cost = node.cachePlacementCost();
        return isCache2DPlacementCandidate(node, cost)
                && (long) cost * (missBodyUses - selectedAncestorUses) >= (long) DagCseOptimizer.CACHE2D_THRESHOLD * missBodyUses;
    }

    private static boolean isCache2DPlacementCandidate(AstNode node, int cost) {
        if (node instanceof CacheLikeNode cacheLikeNode && cacheLikeNode.isFlatCache()) {
            return false;
        }
        return !node.YDependency()
                && cost >= DagCseOptimizer.CACHE2D_THRESHOLD
                && !DagCseOptimizer.containsStrictBoundary(node);
    }
}
