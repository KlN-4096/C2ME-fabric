package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstOptimizer;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.*;

import java.util.IdentityHashMap;

public final class DagCseOptimizer {
    public static final int NOISE_COST = 1024;
    public static final int CACHE2D_THRESHOLD = 64;

    private DagCseOptimizer() {
    }

    public static AstNode optimize(AstNode node) {
        AstNode stripped = stripRebuildableCaches(node);
        AstNode optimized = AstOptimizer.optimizeTree(stripped);
        AstNode dag = HashConsing.deduplicate(optimized);
        CacheUseAnalysis analysis = CacheUseAnalyzer.analyze(dag);
        return new Placement(analysis.boundaryUses(), analysis.regionUses()).place(dag);
    }

    private static AstNode stripRebuildableCaches(AstNode node) {
        return node.transform(astNode -> {
            if (astNode instanceof CacheLikeNode cacheLikeNode && !cacheLikeNode.hasSideEffects()) {
                return cacheLikeNode.getDelegate();
            }
            return astNode;
        });
    }

    static boolean boundaryCrossesStrictCache(AstNode node, IdentityHashMap<AstNode, BoundaryUse> boundaryUses) {
        BoundaryUse boundaryUse = boundaryUses.get(node);
        return boundaryUse != null && boundaryUse.crossesBoundary();
    }

    static boolean containsStrictBoundary(AstNode node) {
        if (node instanceof CacheLikeNode cacheLikeNode && cacheLikeNode.blocksCacheInsertion()) {
            return true;
        }
        for (AstNode child : node.getChildren()) {
            if (containsStrictBoundary(child)) {
                return true;
            }
        }
        return false;
    }

}
