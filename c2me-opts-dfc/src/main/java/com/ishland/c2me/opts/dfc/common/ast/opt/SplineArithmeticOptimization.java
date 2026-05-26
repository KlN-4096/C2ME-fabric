package com.ishland.c2me.opts.dfc.common.ast.opt;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstOptimizer;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.McToAst;
import com.ishland.c2me.opts.dfc.common.ast.binary.AddNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.MulNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.NegNode;
import com.ishland.c2me.opts.dfc.common.vif.AstVanillaInterface;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Spline;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SplineArithmeticOptimization implements AstTransformer {

    private static final SplineArithmeticOptimization INSTANCE = new SplineArithmeticOptimization();

    private SplineArithmeticOptimization() {
    }

    public static AstNode optimize(AstNode node) {
        return node.transform(INSTANCE);
    }

    @Override
    public AstNode transform(AstNode astNode) {
        return switch (astNode) {
            case SplineAstNode n -> n.optimizeLocationAffine();
            case AddNode n -> optimizeAdd(n);
            case MulNode n -> optimizeMul(n);
            case NegNode n -> optimizeNeg(n);
            default -> astNode;
        };
    }

    private AstNode optimizeAdd(AddNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        if (left instanceof SplineAstNode spline && right instanceof ConstantNode constant) {
            float offset = (float) constant.getValue();
            if (offset != 0.0F) {
                return spline.offsetValues(offset);
            }
        }
        if (left instanceof ConstantNode constant && right instanceof SplineAstNode spline) {
            float offset = (float) constant.getValue();
            if (offset != 0.0F) {
                return spline.offsetValues(offset);
            }
        }

        return n;
    }

    private AstNode optimizeMul(MulNode n) {
        AstNode left = n.getLeft();
        AstNode right = n.getRight();

        if (left instanceof SplineAstNode spline && right instanceof ConstantNode constant) {
            return scaleSpline(n, spline, constant);
        }
        if (left instanceof ConstantNode constant && right instanceof SplineAstNode spline) {
            return scaleSpline(n, spline, constant);
        }

        return n;
    }

    private AstNode optimizeNeg(NegNode n) {
        if (n.getOperand() instanceof SplineAstNode spline) {
            return spline.scaleValues(-1.0F);
        }
        return n;
    }

    private AstNode scaleSpline(MulNode original, SplineAstNode spline, ConstantNode constant) {
        float factor = (float) constant.getValue();
        if (factor == 0.0F || factor == 1.0F) {
            return original;
        }
        return spline.scaleValues(factor);
    }

    public static Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> scaleValues(
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
            float factor
    ) {
        if (factor == 1.0F) {
            return spline;
        }
        if (spline instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> fixed) {
            return Spline.fixedFloatFunction(fixed.value() * factor);
        }
        if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
            return spline;
        }

        List<Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>> scaledValues = new ArrayList<>(impl.values().size());
        for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
            scaledValues.add(scaleValues(value, factor));
        }

        float[] derivatives = impl.derivatives().clone();
        for (int i = 0; i < derivatives.length; i++) {
            derivatives[i] *= factor;
        }

        float min = impl.min() * factor;
        float max = impl.max() * factor;
        if (min > max) {
            float tmp = min;
            min = max;
            max = tmp;
        }

        return new Spline.Implementation<>(
                impl.locationFunction(),
                impl.locations(),
                scaledValues,
                derivatives,
                min,
                max
        );
    }

    public static Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> offsetValues(
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline,
            float offset
    ) {
        if (offset == 0.0F) {
            return spline;
        }
        if (spline instanceof Spline.FixedFloatFunction<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> fixed) {
            return Spline.fixedFloatFunction(fixed.value() + offset);
        }
        if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
            return spline;
        }

        List<Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>> offsetValues = new ArrayList<>(impl.values().size());
        for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
            offsetValues.add(offsetValues(value, offset));
        }

        return new Spline.Implementation<>(
                impl.locationFunction(),
                impl.locations(),
                offsetValues,
                impl.derivatives(),
                impl.min() + offset,
                impl.max() + offset
        );
    }

    public static Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> optimizeLocationAffine(
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> spline
    ) {
        if (!(spline instanceof Spline.Implementation<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> impl)) {
            return spline;
        }

        boolean changed = false;
        List<Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper>> optimizedValues = new ArrayList<>(impl.values().size());
        for (Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> value : impl.values()) {
            Spline<DensityFunctionTypes.Spline.SplinePos, DensityFunctionTypes.Spline.DensityFunctionWrapper> optimizedValue = optimizeLocationAffine(value);
            optimizedValues.add(optimizedValue);
            changed |= optimizedValue != value;
        }

        DensityFunction originalLocationFunction = impl.locationFunction().function().value();
        AstNode originalLocationAst = McToAst.toAst(originalLocationFunction);
        AstNode locationAst = AstOptimizer.optimize(originalLocationAst);
        boolean locationOptimized = locationAst != originalLocationAst;
        float[] locations = impl.locations();
        float[] derivatives = impl.derivatives();
        AstNode unwrappedLocationAst = locationAst;
        boolean affineChanged = false;
        while (true) {
            if (unwrappedLocationAst instanceof AddNode add) {
                if (add.getRight() instanceof ConstantNode constant) {
                    locations = shiftLocations(locations, (float) constant.getValue());
                    unwrappedLocationAst = add.getLeft();
                    affineChanged = true;
                    continue;
                }
                if (add.getLeft() instanceof ConstantNode constant) {
                    locations = shiftLocations(locations, (float) constant.getValue());
                    unwrappedLocationAst = add.getRight();
                    affineChanged = true;
                    continue;
                }
            }
            if (unwrappedLocationAst instanceof MulNode mul) {
                if (mul.getRight() instanceof ConstantNode constant) {
                    float factor = (float) constant.getValue();
                    if (factor != 0.0F) {
                        locations = scaleLocations(locations, factor);
                        derivatives = scaleLocationDerivatives(derivatives, factor);
                        if (factor < 0.0F) {
                            reverse(locations);
                            reverse(derivatives);
                            Collections.reverse(optimizedValues);
                        }
                        unwrappedLocationAst = mul.getLeft();
                        affineChanged = true;
                        continue;
                    }
                }
                if (mul.getLeft() instanceof ConstantNode constant) {
                    float factor = (float) constant.getValue();
                    if (factor != 0.0F) {
                        locations = scaleLocations(locations, factor);
                        derivatives = scaleLocationDerivatives(derivatives, factor);
                        if (factor < 0.0F) {
                            reverse(locations);
                            reverse(derivatives);
                            Collections.reverse(optimizedValues);
                        }
                        unwrappedLocationAst = mul.getRight();
                        affineChanged = true;
                        continue;
                    }
                }
            }
            if (unwrappedLocationAst instanceof NegNode neg) {
                locations = scaleLocations(locations, -1.0F);
                derivatives = scaleLocationDerivatives(derivatives, -1.0F);
                reverse(locations);
                reverse(derivatives);
                Collections.reverse(optimizedValues);
                unwrappedLocationAst = neg.getOperand();
                affineChanged = true;
                continue;
            }
            break;
        }

        changed |= affineChanged || locationOptimized;
        if (!changed) {
            return spline;
        }

        DensityFunctionTypes.Spline.DensityFunctionWrapper locationFunction = affineChanged || locationOptimized
                ? new DensityFunctionTypes.Spline.DensityFunctionWrapper(RegistryEntry.of(new AstVanillaInterface(unwrappedLocationAst, originalLocationFunction)))
                : impl.locationFunction();
        return new Spline.Implementation<>(
                locationFunction,
                locations,
                optimizedValues,
                derivatives,
                impl.min(),
                impl.max()
        );
    }

    public static float[] shiftLocations(float[] locations, float offset) {
        float[] shifted = locations.clone();
        for (int i = 0; i < shifted.length; i++) {
            shifted[i] -= offset;
        }
        return shifted;
    }

    public static float[] scaleLocations(float[] locations, float factor) {
        float[] scaled = locations.clone();
        for (int i = 0; i < scaled.length; i++) {
            scaled[i] /= factor;
        }
        return scaled;
    }

    public static float[] scaleLocationDerivatives(float[] derivatives, float factor) {
        float[] scaled = derivatives.clone();
        for (int i = 0; i < scaled.length; i++) {
            scaled[i] *= factor;
        }
        return scaled;
    }

    private static void reverse(float[] values) {
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            float tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
    }
}
