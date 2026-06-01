package com.ishland.c2me.opts.dfc.common.ast.noise;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ducks.ISingleInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.Objects;

public class DFTNoiseNode implements AstNode, ISingleInlineableAstNode {

    private static final int NOISE_COST = 1024;

    private final DensityFunction.Noise noise;
    private final double xzScale;
    private final double yScale;

    public DFTNoiseNode(DensityFunction.Noise noise, double xzScale, double yScale) {
        this.noise = Objects.requireNonNull(noise);
        this.xzScale = xzScale;
        this.yScale = yScale;
    }

    @Override
    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.noise.sample(x * xzScale, y * yScale, z * xzScale);
    }

    @Override
    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        for (int i = 0; i < res.length; i++) {
            res[i] = this.noise.sample(x[i] * xzScale, y[i] * yScale, z[i] * xzScale);
        }
    }

    @Override
    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    @Override
    public int costSelf() {
        return NOISE_COST;
    }

    @Override
    public boolean YDependency() {
        return this.yScale != 0.0;
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void emitValueSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.Noise.class, this.noise);

        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.Noise.class));

        emitScaledCoordSingle(m, 1, this.xzScale);
        emitScaledCoordSingle(m, 2, this.yScale);
        emitScaledCoordSingle(m, 3, this.xzScale);

        m.invokevirtual(
                Type.getInternalName(DensityFunction.Noise.class),
                FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_6910$class_7270", "method_42356", "(DDD)D"),
                "(DDD)D",
                false
        );
    }

    @Override
    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        emitValueSingle(context, m, localVarConsumer);
        m.areturn(Type.DOUBLE_TYPE);
    }

    @Override
    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.Noise.class, this.noise);

        context.doCountedLoop(m, localVarConsumer, idx -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);

            {
                m.load(0, InstructionAdapter.OBJECT_TYPE);
                m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.Noise.class));

                emitScaledCoordMulti(m, 2, idx, this.xzScale);
                emitScaledCoordMulti(m, 3, idx, this.yScale);
                emitScaledCoordMulti(m, 4, idx, this.xzScale);

                m.invokevirtual(
                        Type.getInternalName(DensityFunction.Noise.class),
                        FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_6910$class_7270", "method_42356", "(DDD)D"),
                        "(DDD)D",
                        false
                );
            }

            m.astore(Type.DOUBLE_TYPE);
        });

        m.areturn(Type.VOID_TYPE);
    }

    private static void emitScaledCoordSingle(InstructionAdapter m, int coordLocal, double scale) {
        if (isPositiveZero(scale)) {
            m.dconst(0.0);
            return;
        }

        m.load(coordLocal, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        if (Double.compare(scale, 1.0) != 0) {
            m.dconst(scale);
            m.mul(Type.DOUBLE_TYPE);
        }
    }

    private static void emitScaledCoordMulti(InstructionAdapter m, int coordArrayLocal, int idx, double scale) {
        if (isPositiveZero(scale)) {
            m.dconst(0.0);
            return;
        }

        m.load(coordArrayLocal, InstructionAdapter.OBJECT_TYPE);
        m.load(idx, Type.INT_TYPE);
        m.aload(Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        if (Double.compare(scale, 1.0) != 0) {
            m.dconst(scale);
            m.mul(Type.DOUBLE_TYPE);
        }
    }

    private static boolean isPositiveZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(0.0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DFTNoiseNode that = (DFTNoiseNode) o;
        return Double.compare(xzScale, that.xzScale) == 0 && Double.compare(yScale, that.yScale) == 0 && Objects.equals(noise, that.noise);
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + noise.hashCode();
        result = 31 * result + Double.hashCode(xzScale);
        result = 31 * result + Double.hashCode(yScale);

        return result;
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DFTNoiseNode that = (DFTNoiseNode) o;
        return Double.compare(xzScale, that.xzScale) == 0 && Double.compare(yScale, that.yScale) == 0;
    }

    @Override
    public int relaxedHashCode() {
        int result = 1;

        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + Double.hashCode(xzScale);
        result = 31 * result + Double.hashCode(yScale);

        return result;
    }
}
