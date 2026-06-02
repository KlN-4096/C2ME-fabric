package com.ishland.c2me.opts.dfc.common.ast.spline;

import com.ishland.c2me.opts.dfc.common.ast.*;
import com.ishland.c2me.opts.dfc.common.ast.opt.SplineArithmeticOptimization;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import com.ishland.c2me.opts.dfc.common.ducks.ISingleInlineableAstNode;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.vif.AstVanillaInterface;
import com.ishland.c2me.opts.dfc.common.vif.NoisePosVanillaInterface;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Spline;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class SplineAstNode implements AstNode, ISingleInlineableAstNode {

    private static final int DEFAULT_SELF_COST = 32;
    private static final int SPLINE_SELF_COST = 24;
    private static final int SPLINE_FIXED_VALUE_COST = 1;
    private static final int SPLINE_VALUE_SAMPLE_MULTIPLIER = 2;
    private static final int SPLINE_RANGE_STEP_COST = 3;

    public static final String SPLINE_METHOD_DESC = Type.getMethodDescriptor(Type.getType(float.class), Type.getType(int.class), Type.getType(int.class), Type.getType(int.class), Type.getType(EvalType.class));
    private final Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline;
    private final LocationAsts locationAsts;

    public SplineAstNode(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline) {
        this.spline = spline;
        this.locationAsts = LocationAsts.collect(spline);
    }

    private SplineAstNode(
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
            AstNode[] children
    ) {
        this.spline = spline;
        this.locationAsts = LocationAsts.indexed(spline, children);
    }

    public Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> getSpline() {
        return spline;
    }

    public SplineAstNode scaleValues(float factor) {
        Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> scaled = SplineArithmeticOptimization.scaleValues(this.spline, factor);
        return scaled == this.spline ? this : new SplineAstNode(scaled);
    }

    public SplineAstNode offsetValues(float offset) {
        Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> offsetted = SplineArithmeticOptimization.offsetValues(this.spline, offset);
        return offsetted == this.spline ? this : new SplineAstNode(offsetted);
    }

    public SplineAstNode optimizeLocationAffine() {
        Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> optimized = SplineArithmeticOptimization.optimizeLocationAffine(this.spline, this::locationAst);
        return optimized == this.spline ? this : new SplineAstNode(optimized);
    }

    public AstNode locationAstFor(DensityFunction locationFunction) {
        return locationAst(locationFunction);
    }

    private static Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> mapLocationFunctions(
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
            UnaryOperator<AstNode> mapper,
            Function<DensityFunction, AstNode> locationAstResolver
    ) {
        if (spline instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>) {
            return spline;
        }
        if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
            return spline;
        }

        DensityFunction locationFunction = impl.locationFunction().function().value();
        AstNode locationAst = locationAstResolver.apply(locationFunction);
        AstNode mappedLocationAst = mapper.apply(locationAst);
        boolean changed = mappedLocationAst != locationAst;

        List<Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>> mappedValues = new ArrayList<>(impl.values().size());
        for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> mappedValue = mapLocationFunctions(value, mapper, locationAstResolver);
            mappedValues.add(mappedValue);
            changed |= mappedValue != value;
        }

        if (!changed) {
            return spline;
        }

        DensityFunctionTypes.Spline.DensityFunctionWrapper mappedLocationFunction = mappedLocationAst == locationAst
                ? impl.locationFunction()
                : new DensityFunctionTypes.Spline.DensityFunctionWrapper(RegistryEntry.of(new AstVanillaInterface(mappedLocationAst, locationFunction)));
        return new Spline.Implementation<>(
                mappedLocationFunction,
                impl.locations(),
                mappedValues,
                impl.derivatives(),
                impl.min(),
                impl.max()
        );
    }

    private AstNode locationAst(DensityFunction locationFunction) {
        return this.locationAsts.get(locationFunction);
    }

    private static final class LocationAsts {
        private final IdentityHashMap<DensityFunction, Integer> indexes;
        private final AstNode[] children;

        private LocationAsts(IdentityHashMap<DensityFunction, Integer> indexes, AstNode[] children) {
            this.indexes = indexes;
            this.children = children.clone();
        }

        private static LocationAsts collect(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline) {
            IdentityHashMap<DensityFunction, Integer> indexes = new IdentityHashMap<>();
            List<AstNode> children = new ArrayList<>();
            collectChildren(spline, indexes, children);
            return new LocationAsts(indexes, children.toArray(AstNode[]::new));
        }

        private static void collectChildren(
                Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
                IdentityHashMap<DensityFunction, Integer> indexes,
                List<AstNode> children
        ) {
            if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
                return;
            }
            DensityFunction locationFunction = impl.locationFunction().function().value();
            Integer existingIndex = indexes.get(locationFunction);
            if (existingIndex != null) {
                children.add(children.get(existingIndex));
            } else {
                indexes.put(locationFunction, children.size());
                children.add(McToAst.toAst(locationFunction));
            }
            for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
                collectChildren(value, indexes, children);
            }
        }

        private static LocationAsts indexed(
                Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
                AstNode[] children
        ) {
            IdentityHashMap<DensityFunction, Integer> indexes = new IdentityHashMap<>();
            int[] index = {0};
            collectIndexes(spline, children, index, indexes);
            if (index[0] != children.length) {
                throw new IllegalArgumentException("Expected " + index[0] + " spline children, got " + children.length);
            }
            return new LocationAsts(indexes, children);
        }

        private static void collectIndexes(
                Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
                AstNode[] children,
                int[] index,
                IdentityHashMap<DensityFunction, Integer> indexes
        ) {
            if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
                return;
            }
            if (index[0] >= children.length) {
                throw new IllegalArgumentException("Too few spline children");
            }
            DensityFunction locationFunction = impl.locationFunction().function().value();
            indexes.putIfAbsent(locationFunction, index[0]++);
            for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
                collectIndexes(value, children, index, indexes);
            }
        }

        private AstNode[] children() {
            return this.children.clone();
        }

        private AstNode get(DensityFunction locationFunction) {
            Integer index = this.indexes.get(locationFunction);
            if (index == null) {
                throw new IllegalStateException("Missing cached spline location AST for " + locationFunction);
            }
            return this.children[index];
        }
    }

    @Override
    public double evalSingle(int x, int y, int z, EvalType type) {
        return spline.apply(new DensityFunctionTypes.Spline.SplinePos(new NoisePosVanillaInterface(x, y, z, type)));
    }

    @Override
    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        for (int i = 0; i < res.length; i++) {
            res[i] = this.evalSingle(x[i], y[i], z[i], type);
        }
    }

    @Override
    public AstNode[] getChildren() {
        return this.locationAsts.children();
    }

    @Override
    public AstNode withChildren(AstNode[] children) {
        if (children.length != this.locationAsts.children.length) {
            throw new IllegalArgumentException("Expected " + this.locationAsts.children.length + " children for " + this.getClass().getName() + ", got " + children.length);
        }
        int[] index = {0};
        Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> mapped =
                mapLocationFunctions(this.spline, unused -> children[index[0]++], this::locationAst);
        return mapped == this.spline ? this : new SplineAstNode(mapped, children);
    }

    @Override
    public int costSelf() {
        return estimateSplineCost(this.spline, this::locationAst);
    }

    @Override
    public boolean YDependency() {
        return isSplineYDependent(this.spline, this::locationAst);
    }

    @Override
    public int cost() {
        return this.costSelf();
    }

    @Override
    public int cachePlacementCost() {
        return this.cost();
    }

    private static int estimateSplineCost(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline, Function<DensityFunction, AstNode> locationAstResolver) {
        if (spline instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>) {
            return SPLINE_FIXED_VALUE_COST;
        }
        if (spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl) {
            int locationCost = estimateLocationCost(locationAstResolver.apply(impl.locationFunction().function().value()));
            int valueSamples = impl.values().size() <= 1 ? 1 : SPLINE_VALUE_SAMPLE_MULTIPLIER;
            return SPLINE_SELF_COST
                    + locationCost
                    + SPLINE_RANGE_STEP_COST * ceilLog2(Math.max(1, impl.values().size()))
                    + valueSamples * averageValueCost(impl, locationAstResolver);
        }
        return DEFAULT_SELF_COST;
    }

    private static int ceilLog2(int value) {
        if (value <= 1) {
            return 0;
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    private static int estimateLocationCost(AstNode node) {
        if (node instanceof CacheLikeNode cacheLikeNode && cacheLikeNode.isFlatCache()) {
            return cacheLikeNode.costSelf() + estimateLocationCost(cacheLikeNode.getDelegate());
        }
        if (node instanceof CacheLikeNode cacheLikeNode && cacheLikeNode.getCacheLike() == null) {
            return estimateLocationCost(cacheLikeNode.getDelegate());
        }
        return node.cost();
    }

    private static int averageValueCost(Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl, Function<DensityFunction, AstNode> locationAstResolver) {
        int total = 0;
        for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
            total += estimateSplineCost(value, locationAstResolver);
        }
        return impl.values().isEmpty() ? 0 : total / impl.values().size();
    }

    private static boolean isSplineYDependent(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline, Function<DensityFunction, AstNode> locationAstResolver) {
        if (spline instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>) {
            return false;
        }
        if (spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl) {
            if (locationAstResolver.apply(impl.locationFunction().function().value()).YDependency()) {
                return true;
            }
            for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
                if (isSplineYDependent(value, locationAstResolver)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public AstNode transform(AstTransformer transformer) {
        AstNode[] children = this.locationAsts.children;
        AstNode[] transformedChildren = new AstNode[children.length];
        boolean changed = false;
        for (int i = 0; i < children.length; i++) {
            transformedChildren[i] = children[i].transform(transformer);
            changed |= transformedChildren[i] != children[i];
        }
        AstNode transformed = changed ? this.withChildren(transformedChildren) : this;
        return transformer.transform(transformed);
    }

    @Override
    public void emitValueSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef splineMethod = doBytecodeGenSpline(context, this.spline, this::locationAst);
        callSplineSingle(context, m, splineMethod);
        m.cast(Type.FLOAT_TYPE, Type.DOUBLE_TYPE);
    }

    @Override
    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        emitValueSingle(context, m, localVarConsumer);
        m.areturn(Type.DOUBLE_TYPE);
    }

    private static ValuesMethodDef doBytecodeGenSpline(BytecodeGen.Context context, Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline, Function<DensityFunction, AstNode> locationAstResolver) {
        {
            String cachedSplineMethod = context.getCachedSplineMethod(spline);
            if (cachedSplineMethod != null) {
                return new ValuesMethodDef(false, cachedSplineMethod, 0.0F);
            }
        }
        if (spline instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline1) {
            return new ValuesMethodDef(true, null, spline1.value());
        }
        String name = context.nextMethodName("Spline");
        InstructionAdapter m = new InstructionAdapter(
                new AnalyzerAdapter(
                        context.className,
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                        name,
                        SPLINE_METHOD_DESC,
                        context.classWriter.visitMethod(
                                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                                name,
                                SPLINE_METHOD_DESC,
                                null,
                                null
                        )
                )
        );
        List<IntObjectPair<Pair<String, String>>> extraLocals = new ArrayList<>();
        Label start = new Label();
        Label end = new Label();
        m.visitLabel(start);
        BytecodeGen.Context.LocalVarConsumer localVarConsumer = (localName, localDesc) -> {
            int ordinal = extraLocals.size() + 5;
            extraLocals.add(IntObjectPair.of(ordinal, Pair.of(localName, localDesc)));
            return ordinal;
        };

        if (spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl) {
            ValuesMethodDef[] valuesMethods = impl.values().stream()
                    .map(spline1 -> doBytecodeGenSpline(context, spline1, locationAstResolver))
                    .toArray(ValuesMethodDef[]::new);

            String locations = context.newField(float[].class, impl.locations());
            String derivatives = context.newField(float[].class, impl.derivatives());

            int point = localVarConsumer.createLocalVariable("point", Type.FLOAT_TYPE.getDescriptor());
            int rangeForLocation = localVarConsumer.createLocalVariable("rangeForLocation", Type.INT_TYPE.getDescriptor());
            int locArr = localVarConsumer.createLocalVariable("locArr", Type.getDescriptor(float[].class));
            int derArr = localVarConsumer.createLocalVariable("derArr", Type.getDescriptor(float[].class));

            int lastConst = impl.locations().length - 1;

            AstNode locationFunction = locationAstResolver.apply(impl.locationFunction().function().value());
            context.withLocalCse(locationFunction, () -> AstNode.operandCallByteCodeGen(locationFunction, context, m, localVarConsumer));
            m.cast(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
            m.store(point, Type.FLOAT_TYPE);

            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, locations, Type.getDescriptor(float[].class));
            m.store(locArr, InstructionAdapter.OBJECT_TYPE);

            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, derivatives, Type.getDescriptor(float[].class));
            m.store(derArr, InstructionAdapter.OBJECT_TYPE);

            if (valuesMethods.length == 1) {
                m.load(point, Type.FLOAT_TYPE);
                m.load(locArr, InstructionAdapter.OBJECT_TYPE);
                callSplineSingle(context, m, valuesMethods[0]);
                m.load(derArr, InstructionAdapter.OBJECT_TYPE);
                m.iconst(0);
                m.invokestatic(
                        Type.getInternalName(SplineSupport.class),
                        "sampleOutsideRange",
                        Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE),
                        false
                );
                m.areturn(Type.FLOAT_TYPE);
            } else {
                m.load(locArr, InstructionAdapter.OBJECT_TYPE);
                m.load(point, Type.FLOAT_TYPE);
                m.invokestatic(
                        Type.getInternalName(SplineSupport.class),
                        "findRangeForLocation",
                        Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE),
                        false
                );
                m.store(rangeForLocation, Type.INT_TYPE);

                Label lowerOutsideLabel = new Label();
                Label upperOutsideLabel = new Label();
                Label label3 = new Label();
                int middleCount = valuesMethods.length - 1;
                Label[] middleLabels = new Label[middleCount];
                boolean[] jumpGenerated = new boolean[middleCount];
                Label[] jumpLabels = new Label[lastConst + 2];
                jumpLabels[0] = lowerOutsideLabel;
                jumpLabels[lastConst + 1] = upperOutsideLabel;
                for (int i = 0; i < middleCount; i++) {
                    middleLabels[i] = new Label();
                    jumpLabels[i + 1] = middleLabels[i];
                }

                // findRangeForLocation returns -1 below the first location, lastConst above
                // the last location, and [0, lastConst) for interpolated ranges.
                m.load(point, Type.FLOAT_TYPE);
                m.load(rangeForLocation, Type.INT_TYPE);
                m.tableswitch(
                        -1,
                        lastConst,
                        lowerOutsideLabel,
                        jumpLabels
                );

                m.visitLabel(lowerOutsideLabel);
                m.load(locArr, InstructionAdapter.OBJECT_TYPE);
                callSplineSingle(context, m, valuesMethods[0]);
                m.load(derArr, InstructionAdapter.OBJECT_TYPE);
                m.iconst(0);
                m.invokestatic(
                        Type.getInternalName(SplineSupport.class),
                        "sampleOutsideRange",
                        Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE),
                        false
                );
                m.areturn(Type.FLOAT_TYPE);

                m.visitLabel(upperOutsideLabel);
                m.load(locArr, InstructionAdapter.OBJECT_TYPE);
                callSplineSingle(context, m, valuesMethods[lastConst]);
                m.load(derArr, InstructionAdapter.OBJECT_TYPE);
                m.iconst(lastConst);
                m.invokestatic(
                        Type.getInternalName(SplineSupport.class),
                        "sampleOutsideRange",
                        Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE),
                        false
                );
                m.areturn(Type.FLOAT_TYPE);

                for (int i = 0; i < middleCount; i++) {
                    if (jumpGenerated[i]) continue;
                    m.visitLabel(middleLabels[i]);
                    jumpGenerated[i] = true;
                    for (int j = i + 1; j < middleCount; j++) { // deduplication
                        if (valuesMethods[i].equals(valuesMethods[j]) && valuesMethods[i + 1].equals(valuesMethods[j + 1])) {
                            m.visitLabel(middleLabels[j]);
                            jumpGenerated[j] = true;
                        }
                    }
                    callSplineSingle(context, m, valuesMethods[i]);
                    if (valuesMethods[i].equals(valuesMethods[i + 1])) { // splines are pure
                        m.dup();
                    } else {
                        callSplineSingle(context, m, valuesMethods[i + 1]);
                    }
                    m.goTo(label3);
                }

                m.visitLabel(label3);

                m.load(locArr, InstructionAdapter.OBJECT_TYPE);
                m.load(derArr, InstructionAdapter.OBJECT_TYPE);
                m.load(rangeForLocation, Type.INT_TYPE);
                m.invokestatic(
                        Type.getInternalName(SplineSupport.class),
                        "sampleInsideRange",
                        Type.getMethodDescriptor(Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.getType(float[].class), Type.getType(float[].class), Type.INT_TYPE),
                        false
                );
                m.areturn(Type.FLOAT_TYPE);
            }

        } else {
            throw new UnsupportedOperationException(String.format("Unsupported spline implementation: %s", spline.getClass().getName()));
        }

        m.visitLabel(end);
        m.visitLocalVariable("this", context.classDesc, null, start, end, 0);
        m.visitLocalVariable("x", Type.INT_TYPE.getDescriptor(), null, start, end, 1);
        m.visitLocalVariable("y", Type.INT_TYPE.getDescriptor(), null, start, end, 2);
        m.visitLocalVariable("z", Type.INT_TYPE.getDescriptor(), null, start, end, 3);
        m.visitLocalVariable("evalType", Type.getType(EvalType.class).getDescriptor(), null, start, end, 4);
        for (IntObjectPair<Pair<String, String>> local : extraLocals) {
            m.visitLocalVariable(local.right().left(), local.right().right(), null, start, end, local.leftInt());
        }
        m.visitMaxs(0, 0);

        context.cacheSplineMethod(spline, name);

        return new ValuesMethodDef(false, name, 0.0F);
    }

    private static void callSplineSingle(BytecodeGen.Context context, InstructionAdapter m, ValuesMethodDef target) {
        if (target.isConst()) {
            m.fconst(target.constValue());
        } else {
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(1, Type.INT_TYPE);
            m.load(2, Type.INT_TYPE);
            m.load(3, Type.INT_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(context.className, target.generatedMethod(), SPLINE_METHOD_DESC, false);
        }
    }

    private record ValuesMethodDef(boolean isConst, String generatedMethod, float constValue) {
    }

    @Override
    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.delegateToSingle(m, localVarConsumer, this);
        m.areturn(Type.VOID_TYPE);
    }

    private static boolean deepEquals(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a,
                                      Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> b,
                                      Function<DensityFunction, AstNode> aLocationAstResolver,
                                      Function<DensityFunction, AstNode> bLocationAstResolver) {
        if (a instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1 &&
                b instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> b1) {
            return a1.value() == b1.value();
        } else if (a instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1 &&
                b instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> b1) {
            boolean equals1 = Arrays.equals(a1.derivatives(), b1.derivatives()) &&
                    Arrays.equals(a1.locations(), b1.locations()) &&
                    a1.values().size() == b1.values().size() &&
                    aLocationAstResolver.apply(a1.locationFunction().function().value()).equals(bLocationAstResolver.apply(b1.locationFunction().function().value()));
            if (!equals1) return false;
            int size = a1.values().size();
            for (int i = 0; i < size; i++) {
                if (!deepEquals(a1.values().get(i), b1.values().get(i), aLocationAstResolver, bLocationAstResolver)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean deepRelaxedEquals(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a,
                                      Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> b,
                                      Function<DensityFunction, AstNode> aLocationAstResolver,
                                      Function<DensityFunction, AstNode> bLocationAstResolver) {
        if (a instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1 &&
                b instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> b1) {
            return a1.value() == b1.value();
        } else if (a instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1 &&
                b instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> b1) {
            boolean equals1 = a1.values().size() == b1.values().size() &&
                    aLocationAstResolver.apply(a1.locationFunction().function().value()).relaxedEquals(bLocationAstResolver.apply(b1.locationFunction().function().value()));
            if (!equals1) return false;
            int size = a1.values().size();
            for (int i = 0; i < size; i++) {
                if (!deepRelaxedEquals(a1.values().get(i), b1.values().get(i), aLocationAstResolver, bLocationAstResolver)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static int deepHashcode(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a, Function<DensityFunction, AstNode> locationAstResolver) {
        if (a instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1) {
            return Float.hashCode(a1.value());
        } else if (a instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1) {
            int result = 1;

            result = 31 * result + Arrays.hashCode(a1.derivatives());
            result = 31 * result + Arrays.hashCode(a1.locations());
            for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline : a1.values()) {
                result = 31 * result + deepHashcode(spline, locationAstResolver);
            }
            result = 31 * result + locationAstResolver.apply(a1.locationFunction().function().value()).hashCode();

            return result;
        } else {
            return a.hashCode();
        }
    }

    private static int deepRelaxedHashcode(Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a, Function<DensityFunction, AstNode> locationAstResolver) {
        if (a instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1) {
            return Float.hashCode(a1.value());
        } else if (a instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> a1) {
            int result = 1;

            for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline : a1.values()) {
                result = 31 * result + deepRelaxedHashcode(spline, locationAstResolver);
            }
            result = 31 * result + locationAstResolver.apply(a1.locationFunction().function().value()).relaxedHashCode();

            return result;
        } else {
            return a.hashCode();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SplineAstNode that = (SplineAstNode) o;
        return deepEquals(this.spline, that.spline, this::locationAst, that::locationAst);
    }

    @Override
    public int hashCode() {
        return deepHashcode(this.spline, this::locationAst);
    }

    @Override
    public boolean relaxedEquals(AstNode o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SplineAstNode that = (SplineAstNode) o;
        return deepRelaxedEquals(this.spline, that.spline, this::locationAst, that::locationAst);
    }

    @Override
    public int relaxedHashCode() {
        return deepRelaxedHashcode(this.spline, this::locationAst);
    }
}
