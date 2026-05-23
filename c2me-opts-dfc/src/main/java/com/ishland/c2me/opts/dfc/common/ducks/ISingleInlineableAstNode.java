package com.ishland.c2me.opts.dfc.common.ducks;

import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.commons.InstructionAdapter;

public interface ISingleInlineableAstNode {

    // Emits bytecode pushing the computed double value onto the stack.
    // Caller handles return or further computation so no emit RETURN.
    void emitValueSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer);

}
