package com.ishland.c2me.opts.dfc.common.ast.opt;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.RangeChoiceNode;

public final class RangeChoicePruning implements AstTransformer {

    private static final RangeChoicePruning INSTANCE = new RangeChoicePruning();

    private RangeChoicePruning() {}

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        if (astNode instanceof RangeChoiceNode n) {
            return prune(n);
        }
        return astNode;
    }

    private AstNode prune(RangeChoiceNode n) {
        AstNode input = n.getInput();
        double min = n.getMinInclusive();
        double max = n.getMaxExclusive();
        AstNode whenInRange = n.getWhenInRange();
        AstNode whenOutOfRange = n.getWhenOutOfRange();

        // Empty range [min, max) with min >= max is always out-of-range
        if (min >= max) {
            return whenOutOfRange;
        }

        // Dead branch elimination: constant input resolves statically
        if (input instanceof ConstantNode c) {
            double v = c.getValue();
            return (v >= min && v < max) ? whenInRange : whenOutOfRange;
        }

        // In-range nested pruning: whenInRange = RangeChoice(input, [iMin,iMax), X, Y)
        // We know input ∈ [min,max), so the inner condition can be simplified.
        if (whenInRange instanceof RangeChoiceNode inner && inner.getInput().equals(input)) {
            double iMin = inner.getMinInclusive();
            double iMax = inner.getMaxExclusive();

            // [min,max) ⊆ [iMin,iMax): inner condition always true inside outer in-range
            if (min >= iMin && max <= iMax) {
                return new RangeChoiceNode(input, min, max, inner.getWhenInRange(), whenOutOfRange);
            }

            // [min,max) and [iMin,iMax) are disjoint: inner condition always false inside outer in-range
            if (max <= iMin || min >= iMax) {
                return new RangeChoiceNode(input, min, max, inner.getWhenOutOfRange(), whenOutOfRange);
            }
        }

        // Out-of-range nested pruning: whenOutOfRange = RangeChoice(input, [iMin,iMax), Y, Z)
        // We know input ∉ [min,max). If [iMin,iMax) ⊆ [min,max), the inner can never fire.
        if (whenOutOfRange instanceof RangeChoiceNode inner && inner.getInput().equals(input)) {
            double iMin = inner.getMinInclusive();
            double iMax = inner.getMaxExclusive();

            // [iMin,iMax) ⊆ [min,max): inner can never be true when input ∉ [min,max)
            if (iMin >= min && iMax <= max) {
                return new RangeChoiceNode(input, min, max, whenInRange, inner.getWhenOutOfRange());
            }
        }

        return n;
    }
}
