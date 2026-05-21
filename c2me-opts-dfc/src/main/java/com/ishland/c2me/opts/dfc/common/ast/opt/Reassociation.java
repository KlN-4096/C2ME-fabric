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

public final class Reassociation implements AstTransformer {

    private static final Reassociation INSTANCE = new Reassociation();

    private Reassociation() {}

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case AddNode n -> processAdd(n);
            case MulNode n -> processMul(n);
            case MinNode n -> processMin(n);
            case MaxNode n -> processMax(n);
            case AbsNode n -> processAbs(n);
            default -> astNode;
        };
    }

    private AstNode processAdd(AddNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Canonicalize: Add(Const, x) -> Add(x, Const)
        if (left instanceof ConstantNode && !(right instanceof ConstantNode)) {
            return new AddNode(right, left);
        }

        // Reassociate: Add(Add(x, Const(a)), Const(b)) -> Add(x, Const(a+b))
        if (right instanceof ConstantNode cRight
                && left instanceof AddNode inner
                && inner.getRight() instanceof ConstantNode cInner) {
            return new AddNode(inner.getLeft(), new ConstantNode(cInner.getValue() + cRight.getValue()));
        }

        return n;
    }

    private AstNode processMul(MulNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        // Canonicalize: Mul(Const, x) -> Mul(x, Const)
        if (left instanceof ConstantNode && !(right instanceof ConstantNode)) {
            return new MulNode(right, left);
        }

        // Reassociate: Mul(Mul(x, Const(a)), Const(b)) -> Mul(x, Const(a*b))
        if (right instanceof ConstantNode cRight
                && left instanceof MulNode inner
                && inner.getRight() instanceof ConstantNode cInner) {
            return new MulNode(inner.getLeft(), new ConstantNode(cInner.getValue() * cRight.getValue()));
        }

        return n;
    }

    // Canonicalize: Min(Const, x) -> Min(x, Const)
    private AstNode processMin(MinNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        if (left instanceof ConstantNode && !(right instanceof ConstantNode)) {
            return new MinNode(right, left);
        }

        return n;
    }

    // Canonicalize: Max(Const, x) -> Max(x, Const)
    private AstNode processMax(MaxNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        if (left instanceof ConstantNode && !(right instanceof ConstantNode)) {
            return new MaxNode(right, left);
        }

        return n;
    }

    // Abs(Cube(x)) -> Cube(Abs(x)): pushes Abs inward, since sign(x^3) == sign(x)
    // This exposes the inner Abs(x) to further elimination by outer operators.
    private AstNode processAbs(AbsNode n) {
        if (n.getOperand() instanceof CubeNode cube) {
            return new CubeNode(new AbsNode(cube.getOperand()));
        }
        return n;
    }
}
