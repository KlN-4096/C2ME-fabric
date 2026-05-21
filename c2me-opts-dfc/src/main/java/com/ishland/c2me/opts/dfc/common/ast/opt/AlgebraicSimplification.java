package com.ishland.c2me.opts.dfc.common.ast.opt;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.binary.*;
import com.ishland.c2me.opts.dfc.common.ast.misc.*;
import com.ishland.c2me.opts.dfc.common.ast.unary.*;

public final class AlgebraicSimplification implements AstTransformer {

    private static final AlgebraicSimplification INSTANCE = new AlgebraicSimplification();

    private AlgebraicSimplification() {}

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case AddNode n -> simplifyAdd(n);
            case MulNode n -> simplifyMul(n);
            case NegMulNode n -> simplifyNegMul(n);
            case NegNode n -> simplifyNeg(n);
            case AbsNode n -> simplifyAbs(n);
            case SquareNode n -> simplifySquare(n);
            case CubeNode n -> simplifyCube(n);
            case SqueezeNode n -> simplifySqueeze(n);
            default -> astNode;
        };
    }

    private AstNode simplifyAdd(AddNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Add(x, 0) -> x
        if (isConstant(right, 0.0)) {
            return left;
        }
        // Add(0, x) -> x
        if (isConstant(left, 0.0)) {
            return right;
        }

        return n;
    }

    private AstNode simplifyMul(MulNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Mul(x, 1) -> x
        if (isConstant(right, 1.0)) {
            return left;
        }
        // Mul(1, x) -> x
        if (isConstant(left, 1.0)) {
            return right;
        }
        // Mul(x, 0) -> 0
        if (isConstant(right, 0.0) || isConstant(left, 0.0)) {
            return new ConstantNode(0.0);
        }

        return n;
    }

    private AstNode simplifyNegMul(NegMulNode n) {
        // NegMul(x, 1.0) -> x
        if (n.getNegMul() == 1.0) {
            return n.getOperand();
        }
        // NegMul(x, -1.0) -> Abs(x): v>0?v:v*(-1) == |v|
        if (n.getNegMul() == -1.0) {
            return new AbsNode(n.getOperand());
        }
        return n;
    }

    private AstNode simplifyNeg(NegNode n) {
        // Neg(Neg(x)) -> x
        if (n.getOperand() instanceof NegNode inner) {
            return inner.getOperand();
        }
        return n;
    }

    private AstNode simplifyAbs(AbsNode n) {
        // Abs(Abs(x)) -> Abs(x)
        if (n.getOperand() instanceof AbsNode) {
            return n.getOperand();
        }
        return n;
    }

    private AstNode simplifySquare(SquareNode n) {
        // Square(0) -> 0, Square(1) -> 1
        if (isConstant(n.getOperand(), 0.0)) return new ConstantNode(0.0);
        if (isConstant(n.getOperand(), 1.0)) return new ConstantNode(1.0);
        return n;
    }

    private AstNode simplifyCube(CubeNode n) {
        // Cube(0) -> 0, Cube(1) -> 1
        if (isConstant(n.getOperand(), 0.0)) return new ConstantNode(0.0);
        if (isConstant(n.getOperand(), 1.0)) return new ConstantNode(1.0);
        return n;
    }

    private AstNode simplifySqueeze(SqueezeNode n) {
        // Squeeze(Squeeze(x)) -> Squeeze(x)
        if (n.getOperand() instanceof SqueezeNode) {
            return n.getOperand();
        }
        return n;
    }

    private static boolean isConstant(AstNode node, double value) {
        return node instanceof ConstantNode c && Double.compare(c.getValue(), value) == 0;
    }
}
