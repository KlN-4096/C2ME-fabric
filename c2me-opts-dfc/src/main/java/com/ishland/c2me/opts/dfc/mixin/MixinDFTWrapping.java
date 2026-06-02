package com.ishland.c2me.opts.dfc.mixin;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.ducks.IEqualityOverriding;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;

@Mixin(DensityFunctionTypes.Wrapping.class)
public abstract class MixinDFTWrapping implements IFastCacheLike, IEqualityOverriding, DensityFunctionTypes.Wrapper {

    @Mutable
    @Shadow @Final private DensityFunction wrapped;

    @Shadow public abstract DensityFunctionTypes.Wrapping.Type type();

    @Unique
    private Object c2me$optionalEquality;
    @Unique
    private boolean c2me$cacheSet;
    @Unique
    private int c2me$lastX;
    @Unique
    private int c2me$lastY;
    @Unique
    private int c2me$lastZ;
    @Unique
    private long c2me$lastColumn;
    @Unique
    private double c2me$lastValue;
    @Unique
    private int[] c2me$lastXa;
    @Unique
    private int[] c2me$lastYa;
    @Unique
    private int[] c2me$lastZa;
    @Unique
    private long[] c2me$lastColumna;
    @Unique
    private double[] c2me$lastValuea;

    @Override
    public double c2me$getCached(int x, int y, int z, EvalType evalType) {
        DensityFunctionTypes.Wrapping.Type type = this.type();
        if (this.c2me$lastValuea != null) {
            if (type == DensityFunctionTypes.Wrapping.Type.CACHE2D) {
                long column = ChunkPos.toLong(x, z);
                for (int i = 0; i < this.c2me$lastValuea.length; i++) {
                    if (this.c2me$lastColumna[i] == column) {
                        return this.c2me$lastValuea[i];
                    }
                }
            } else if (type == DensityFunctionTypes.Wrapping.Type.CACHE_ONCE) {
                for (int i = 0; i < this.c2me$lastValuea.length; i++) {
                    if (this.c2me$lastXa[i] == x && this.c2me$lastYa[i] == y && this.c2me$lastZa[i] == z) {
                        return this.c2me$lastValuea[i];
                    }
                }
            }
        }
        if (!this.c2me$cacheSet) {
            return Double.longBitsToDouble(CACHE_MISS_NAN_BITS);
        }
        if (type == DensityFunctionTypes.Wrapping.Type.CACHE2D) {
            return this.c2me$lastColumn == ChunkPos.toLong(x, z) ? this.c2me$lastValue : Double.longBitsToDouble(CACHE_MISS_NAN_BITS);
        }
        if (type == DensityFunctionTypes.Wrapping.Type.CACHE_ONCE) {
            return this.c2me$lastX == x && this.c2me$lastY == y && this.c2me$lastZ == z ? this.c2me$lastValue : Double.longBitsToDouble(CACHE_MISS_NAN_BITS);
        }
        return Double.longBitsToDouble(CACHE_MISS_NAN_BITS);
    }

    @Override
    public boolean c2me$getCached(double[] res, int[] x, int[] y, int[] z, EvalType evalType) {
        if (this.c2me$lastValuea == null || this.c2me$lastValuea.length != res.length) {
            return false;
        }
        if (this.type() == DensityFunctionTypes.Wrapping.Type.CACHE2D) {
            if (!c2me$matchesColumns(x, z)) {
                return false;
            }
        } else if (this.type() == DensityFunctionTypes.Wrapping.Type.CACHE_ONCE) {
            if (!Arrays.equals(x, this.c2me$lastXa) || !Arrays.equals(y, this.c2me$lastYa) || !Arrays.equals(z, this.c2me$lastZa)) {
                return false;
            }
        } else {
            return false;
        }
        System.arraycopy(this.c2me$lastValuea, 0, res, 0, this.c2me$lastValuea.length);
        return true;
    }

    @Override
    public void c2me$cache(int x, int y, int z, EvalType evalType, double cached) {
        if (this.type() == DensityFunctionTypes.Wrapping.Type.CACHE2D) {
            this.c2me$lastColumn = ChunkPos.toLong(x, z);
            this.c2me$lastValue = cached;
            this.c2me$cacheSet = true;
        } else if (this.type() == DensityFunctionTypes.Wrapping.Type.CACHE_ONCE) {
            this.c2me$lastX = x;
            this.c2me$lastY = y;
            this.c2me$lastZ = z;
            this.c2me$lastValue = cached;
            this.c2me$cacheSet = true;
        }
    }

    @Override
    public void c2me$cache(double[] res, int[] x, int[] y, int[] z, EvalType evalType) {
        if (this.type() == DensityFunctionTypes.Wrapping.Type.CACHE2D) {
            c2me$cacheColumns(res, x, z);
        } else if (this.type() == DensityFunctionTypes.Wrapping.Type.CACHE_ONCE) {
            c2me$cachePositions(res, x, y, z);
        }
    }

    @Unique
    private boolean c2me$matchesColumns(int[] x, int[] z) {
        if (this.c2me$lastColumna == null || this.c2me$lastColumna.length != x.length) {
            return false;
        }
        for (int i = 0; i < x.length; i++) {
            if (this.c2me$lastColumna[i] != ChunkPos.toLong(x[i], z[i])) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private void c2me$cacheColumns(double[] res, int[] x, int[] z) {
        c2me$ensureValueArray(res);
        if (this.c2me$lastColumna == null || this.c2me$lastColumna.length != res.length) {
            this.c2me$lastColumna = new long[res.length];
        }
        for (int i = 0; i < res.length; i++) {
            this.c2me$lastColumna[i] = ChunkPos.toLong(x[i], z[i]);
        }
    }

    @Unique
    private void c2me$cachePositions(double[] res, int[] x, int[] y, int[] z) {
        c2me$ensureValueArray(res);
        this.c2me$lastXa = c2me$copyOrReplace(this.c2me$lastXa, x);
        this.c2me$lastYa = c2me$copyOrReplace(this.c2me$lastYa, y);
        this.c2me$lastZa = c2me$copyOrReplace(this.c2me$lastZa, z);
    }

    @Unique
    private void c2me$ensureValueArray(double[] res) {
        if (this.c2me$lastValuea == null || this.c2me$lastValuea.length != res.length) {
            this.c2me$lastValuea = Arrays.copyOf(res, res.length);
        } else {
            System.arraycopy(res, 0, this.c2me$lastValuea, 0, res.length);
        }
    }

    @Unique
    private static int[] c2me$copyOrReplace(int[] current, int[] source) {
        if (current == null || current.length != source.length) {
            return Arrays.copyOf(source, source.length);
        }
        System.arraycopy(source, 0, current, 0, source.length);
        return current;
    }

    @Override
    public DensityFunction c2me$getDelegate() {
        return this.wrapped;
    }

    @Override
    public DensityFunction c2me$withDelegate(DensityFunction delegate) {
        DensityFunctionTypes.Wrapping wrapping = new DensityFunctionTypes.Wrapping(this.type(), delegate);
        ((IEqualityOverriding) (Object) wrapping).c2me$overrideEquality(this);
        return wrapping;
    }

    @Override
    public void c2me$overrideEquality(Object object) {
        Object inner = object;
        while (true) {
            Object inner1 = inner instanceof IEqualityOverriding e1 ? e1.c2me$getOverriddenEquality() : null;
            if (inner1 == null) {
                this.c2me$optionalEquality = inner;
                break;
            }
            inner = inner1;
        }
    }

    @Override
    public Object c2me$getOverriddenEquality() {
        return this.c2me$optionalEquality;
    }

    @WrapMethod(method = "hashCode")
    private int wrapHashCode(Operation<Integer> original) {
        Object c2me$optionalEquality1 = this.c2me$optionalEquality;
        if (c2me$optionalEquality1 != null) {
            return c2me$optionalEquality1.hashCode();
        } else {
            return original.call();
        }
    }

    @WrapMethod(method = "equals")
    private boolean wrapEquals(Object that, Operation<Boolean> original) {
        Object a = this.c2me$getOverriddenEquality();
        Object b = that instanceof IEqualityOverriding equalityOverriding ? equalityOverriding.c2me$getOverriddenEquality() : null;
        if (a == null) {
            return original.call(b != null ? b : that);
        } else {
            return a.equals(b != null ? b : that);
        }
    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        return visitor.apply(this.c2me$withDelegate(this.wrapped().apply(visitor)));
    }

    @Override
    public String c2me$getName() {
        return "Wrapper" + this.type().name();
    }
}
