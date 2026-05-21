package com.ishland.c2me.opts.dfc.common.ast.opt;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxShortNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinShortNode;

public final class IdentityElimination implements AstTransformer {

    private static final IdentityElimination INSTANCE = new IdentityElimination();

    private IdentityElimination() {}

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case MinNode n -> eliminateMin(n);
            case MaxNode n -> eliminateMax(n);
            case MinShortNode n -> eliminateMinShort(n);
            case MaxShortNode n -> eliminateMaxShort(n);
            default -> astNode;
        };
    }

    private AstNode eliminateMin(MinNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Min(x, x) -> x
        if (left.equals(right)) {
            return left;
        }
        // Min(x, Max(y, x)) -> x
        if (right instanceof MaxNode max && (left.equals(max.getLeft()) || left.equals(max.getRight()))) {
            return left;
        }
        // Min(Max(x, y), x) -> x
        if (left instanceof MaxNode max && (right.equals(max.getLeft()) || right.equals(max.getRight()))) {
            return right;
        }

        return n;
    }

    private AstNode eliminateMax(MaxNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Max(x, x) -> x
        if (left.equals(right)) {
            return left;
        }
        // Max(x, Min(y, x)) -> x
        if (right instanceof MinNode min && (left.equals(min.getLeft()) || left.equals(min.getRight()))) {
            return left;
        }
        // Max(Min(x, y), x) -> x
        if (left instanceof MinNode min && (right.equals(min.getLeft()) || right.equals(min.getRight()))) {
            return right;
        }

        return n;
    }

    private AstNode eliminateMinShort(MinShortNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // MinShort(x, x, c) -> x
        if (left.equals(right)) {
            return left;
        }

        return n;
    }

    private AstNode eliminateMaxShort(MaxShortNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // MaxShort(x, x, c) -> x
        if (left.equals(right)) {
            return left;
        }

        return n;
    }
}
