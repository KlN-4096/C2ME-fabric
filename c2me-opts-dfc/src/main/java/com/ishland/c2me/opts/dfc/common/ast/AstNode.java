package com.ishland.c2me.opts.dfc.common.ast;

import com.ishland.c2me.opts.dfc.common.ducks.ISingleInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.ducks.IMultiInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.commons.InstructionAdapter;

public interface AstNode {

    double evalSingle(int x, int y, int z, EvalType type);

    void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type);

    AstNode[] getChildren();

    AstNode transform(AstTransformer transformer);

    void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer);

    void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer);

    static void operandCallByteCodeGen(AstNode operand, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        if (operand instanceof ISingleInlineableAstNode inlineable) {
            inlineable.emitValueSingle(context, m, localVarConsumer);
        } else {
            String operandMethod = context.newSingleMethod(operand);
            context.callDelegateSingle(m, operandMethod);
        }
    }

    static void operandCallMultiByteCodeGen(AstNode operand, BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer, int resultArrayLocal) {
        if (operand instanceof IMultiInlineableAstNode inlineable) {
            inlineable.emitMulti(context, m, localVarConsumer, resultArrayLocal);
        } else {
            String operandMethod = context.newMultiMethod(operand);
            context.callDelegateMulti(m, operandMethod, resultArrayLocal);
        }
    }

    // data to be created as fields in generated code are only compared by class type
    boolean relaxedEquals(AstNode o);

    int relaxedHashCode();

}
