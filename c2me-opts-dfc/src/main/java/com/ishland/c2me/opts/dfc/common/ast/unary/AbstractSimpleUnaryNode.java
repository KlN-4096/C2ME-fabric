package com.ishland.c2me.opts.dfc.common.ast.unary;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ducks.IMultiInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public abstract class AbstractSimpleUnaryNode extends AbstractUnaryNode implements IMultiInlineableAstNode {

    public AbstractSimpleUnaryNode(AstNode operand) {
        super(operand);
    }

    @Override
    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        emitMulti(context, m, localVarConsumer, 1);
        m.areturn(Type.VOID_TYPE);
    }

    @Override
    public void emitMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer, int resultArrayLocal) {
        AstNode.operandCallMultiByteCodeGen(this.operand, context, m, localVarConsumer, resultArrayLocal);
        context.doCountedLoop(m, localVarConsumer, idx -> bytecodeGenMultiBody(m, idx, resultArrayLocal));
    }

    protected abstract void bytecodeGenMultiBody(InstructionAdapter m, int idx, int resultArrayLocal);

}
