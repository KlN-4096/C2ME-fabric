package com.ishland.c2me.opts.dfc.common.ast.opt;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.binary.AddNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MaxNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MinNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MulNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.AbsNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.CubeNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegMulNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SquareNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.SqueezeNode;
import net.minecraft.util.math.MathHelper;

public final class ConstantFolding implements AstTransformer {

    private static final ConstantFolding INSTANCE = new ConstantFolding();

    private ConstantFolding() {}

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case AddNode n -> foldBinary(n, n.getLeft(), n.getRight(), Double::sum);
            case MulNode n -> foldBinary(n, n.getLeft(), n.getRight(), (a, b) -> a * b);
            case MinNode n -> foldBinary(n, n.getLeft(), n.getRight(), Math::min);
            case MaxNode n -> foldBinary(n, n.getLeft(), n.getRight(), Math::max);
            case AbsNode n -> foldUnary(n, n.getOperand(), Math::abs);
            case NegNode n -> foldUnary(n, n.getOperand(), a -> -a);
            case SquareNode n -> foldUnary(n, n.getOperand(), a -> a * a);
            case CubeNode n -> foldUnary(n, n.getOperand(), a -> a * a * a);
            case SqueezeNode n -> foldUnary(n, n.getOperand(), this::squeeze);
            case NegMulNode n -> foldNegMul(n);
            default -> astNode;
        };
    }

    private AstNode foldBinary(AstNode original, AstNode left, AstNode right, DoubleBinaryOperator op) {
        if (left instanceof ConstantNode cLeft && right instanceof ConstantNode cRight) {
            return new ConstantNode(op.applyAsDouble(cLeft.getValue(), cRight.getValue()));
        }
        return original;
    }

    private AstNode foldUnary(AstNode original, AstNode operand, DoubleUnaryOperator op) {
        if (operand instanceof ConstantNode c) {
            return new ConstantNode(op.applyAsDouble(c.getValue()));
        }
        return original;
    }

    private AstNode foldNegMul(NegMulNode n) {
        if (n.getOperand() instanceof ConstantNode c) {
            double v = c.getValue();
            double result = v > 0.0 ? v : v * n.getNegMul();
            return new ConstantNode(result);
        }
        return n;
    }

    private double squeeze(double v) {
        v = MathHelper.clamp(v, -1.0, 1.0);
        return v / 2.0 - v * v * v / 24.0;
    }

    @FunctionalInterface
    private interface DoubleBinaryOperator {
        double applyAsDouble(double left, double right);
    }

    @FunctionalInterface
    private interface DoubleUnaryOperator {
        double applyAsDouble(double operand);
    }
}
