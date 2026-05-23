package com.ishland.c2me.opts.dfc.common.ast.binary;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ducks.IMultiInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.ducks.ISingleInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.util.ArrayCache;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public abstract class AbstractSimpleBinaryNode extends AbstractBinaryNode implements ISingleInlineableAstNode, IMultiInlineableAstNode {

    public AbstractSimpleBinaryNode(AstNode left, AstNode right) {
        super(left, right);
    }

    @Override
    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        emitMulti(context, m, localVarConsumer, 1);
        m.areturn(Type.VOID_TYPE);
    }

    @Override
    public void emitMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer, int resultArrayLocal) {
        int res1 = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(double[].class));

        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(resultArrayLocal, InstructionAdapter.OBJECT_TYPE);
        m.arraylength();
        m.iconst(0);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE), false);
        m.store(res1, InstructionAdapter.OBJECT_TYPE);

        AstNode.operandCallMultiByteCodeGen(this.left, context, m, localVarConsumer, resultArrayLocal);
        AstNode.operandCallMultiByteCodeGen(this.right, context, m, localVarConsumer, res1);
        context.doCountedLoop(m, localVarConsumer, idx -> bytecodeGenMultiBody(m, idx, res1, resultArrayLocal));

        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(res1, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class)), false);
    }

    protected abstract void bytecodeGenMultiBody(InstructionAdapter m, int idx, int res1, int resultArrayLocal);

}
