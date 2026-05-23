package com.ishland.c2me.opts.dfc.common.ducks;

import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.commons.InstructionAdapter;

public interface IMultiInlineableAstNode {

    // Emits bytecode filling the double[] stored in resultArrayLocal.
    // Caller handles return so no emit RETURN.
    void emitMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer, int resultArrayLocal);

}
