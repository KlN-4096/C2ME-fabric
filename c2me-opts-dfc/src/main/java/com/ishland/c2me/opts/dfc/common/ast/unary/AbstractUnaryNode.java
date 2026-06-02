package com.ishland.c2me.opts.dfc.common.ast.unary;

import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.Objects;

public abstract class AbstractUnaryNode implements AstNode {

    private static final int UNARY_SELF_COST = 2;

    protected final AstNode operand;

    public AbstractUnaryNode(AstNode operand) {
        this.operand = Objects.requireNonNull(operand);
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[]{operand};
    }

    @Override
    public AstNode withChildren(AstNode[] children) {
        if (children.length != 1) {
            throw new IllegalArgumentException("Expected 1 child for " + this.getClass().getName() + ", got " + children.length);
        }
        return newInstance(children[0]);
    }

    @Override
    public int costSelf() {
        return UNARY_SELF_COST;
    }

    @Override
    public boolean YDependency() {
        return this.operand.YDependency();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractUnaryNode that = (AbstractUnaryNode) o;
        return Objects.equals(operand, that.operand);
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + operand.hashCode();

        return result;
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractUnaryNode that = (AbstractUnaryNode) o;
        return operand.relaxedEquals(that.operand);
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + operand.relaxedHashCode();

        return result;
    }

    protected abstract AstNode newInstance(AstNode operand);

    public AstNode getOperand() {
        return operand;
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode operand = this.operand.transform(transformer);
        if (this.operand == operand) {
            return transformer.transform(this);
        } else {
            return transformer.transform(newInstance(operand));
        }
    }

    @Override
    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.delegateToSingle(m, localVarConsumer, this);
        m.areturn(Type.VOID_TYPE);
    }
}
