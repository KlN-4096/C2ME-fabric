package com.ishland.c2me.opts.dfc.common.ast.opt;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.binary.MulNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.AbsNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.CubeNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegMulNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SquareNode;

public final class StrengthReduction implements AstTransformer {

    private static final StrengthReduction INSTANCE = new StrengthReduction();

    private StrengthReduction() {}

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case MulNode n -> reduceMul(n);
            case AbsNode n -> reduceAbs(n);
            case SquareNode n -> reduceSquare(n);
            case NegMulNode n -> reduceNegMul(n);
            default -> astNode;
        };
    }

    private AstNode reduceMul(MulNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Mul(x, x) -> Square(x): evaluate x once, no zero-check branch
        if (left.equals(right)) {
            return new SquareNode(left);
        }

        // Mul(Square(x), x) -> Cube(x)
        if (left instanceof SquareNode sq && sq.getOperand().equals(right)) {
            return new CubeNode(right);
        }

        // Mul(x, Square(x)) -> Cube(x)
        if (right instanceof SquareNode sq && sq.getOperand().equals(left)) {
            return new CubeNode(left);
        }

        // Mul(x, -1) -> Neg(x): after Reassociation canonicalizes Mul(Const(-1), x) -> Mul(x, Const(-1))
        if (right instanceof ConstantNode c && Double.compare(c.getValue(), -1.0) == 0) {
            return new NegNode(left);
        }

        return n;
    }

    // Abs(x) -> x when x is provably non-negative (x^2 >= 0, |x| >= 0, constant >= 0)
    private AstNode reduceAbs(AbsNode n) {
        if (isNonNegative(n.getOperand())) {
            return n.getOperand();
        }
        return n;
    }

    // Square(Abs(x)) -> Square(x): |x|^2 == x^2
    private AstNode reduceSquare(SquareNode n) {
        if (n.getOperand() instanceof AbsNode abs) {
            return new SquareNode(abs.getOperand());
        }
        return n;
    }

    // NegMul(x, c) -> x when x >= 0: the negative branch is never taken
    private AstNode reduceNegMul(NegMulNode n) {
        if (isNonNegative(n.getOperand())) {
            return n.getOperand();
        }
        return n;
    }

    private static boolean isNonNegative(AstNode node) {
        return switch (node) {
            case SquareNode n -> true;
            case AbsNode n -> true;
            case ConstantNode c -> c.getValue() >= 0.0;
            default -> false;
        };
    }
}
