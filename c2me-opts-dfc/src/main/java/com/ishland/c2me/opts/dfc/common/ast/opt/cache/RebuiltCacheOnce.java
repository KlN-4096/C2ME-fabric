package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.ducks.IRebuiltCacheLike;
import com.ishland.c2me.opts.dfc.common.vif.EachApplierVanillaInterface;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.Arrays;
import java.util.Objects;

public final class RebuiltCacheOnce implements IFastCacheLike, IRebuiltCacheLike {

    private DensityFunction delegate;
    private boolean cacheSet;
    private int lastX;
    private int lastY;
    private int lastZ;
    private double lastValue;
    private int[] lastXa;
    private int[] lastYa;
    private int[] lastZa;
    private double[] lastValuea;

    RebuiltCacheOnce(DensityFunction delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public double sample(NoisePos pos) {
        int x = pos.blockX();
        int y = pos.blockY();
        int z = pos.blockZ();
        double cached = this.c2me$getCached(x, y, z, EvalType.from(pos));
        if (Double.doubleToRawLongBits(cached) != CACHE_MISS_NAN_BITS) {
            return cached;
        }
        double sampled = this.delegate.sample(pos);
        this.c2me$cache(x, y, z, EvalType.from(pos), sampled);
        return sampled;
    }

    @Override
    public void fill(double[] densities, EachApplier applier) {
        if (applier instanceof EachApplierVanillaInterface vanilla
                && this.c2me$getCached(densities, vanilla.getX(), vanilla.getY(), vanilla.getZ(), EvalType.from(applier))) {
            return;
        }
        this.delegate.fill(densities, applier);
        if (applier instanceof EachApplierVanillaInterface vanilla) {
            this.c2me$cache(densities, vanilla.getX(), vanilla.getY(), vanilla.getZ(), EvalType.from(applier));
        }
    }

    @Override
    public double c2me$getCached(int x, int y, int z, EvalType evalType) {
        if (this.lastValuea != null) {
            for (int i = 0; i < this.lastValuea.length; i++) {
                if (this.lastXa[i] == x && this.lastYa[i] == y && this.lastZa[i] == z) {
                    return this.lastValuea[i];
                }
            }
        }
        if (this.cacheSet && this.lastX == x && this.lastY == y && this.lastZ == z) {
            return this.lastValue;
        }
        return Double.longBitsToDouble(CACHE_MISS_NAN_BITS);
    }

    @Override
    public boolean c2me$getCached(double[] res, int[] x, int[] y, int[] z, EvalType evalType) {
        if (this.lastValuea == null || this.lastValuea.length != res.length) {
            return false;
        }
        if (!Arrays.equals(x, this.lastXa) || !Arrays.equals(y, this.lastYa) || !Arrays.equals(z, this.lastZa)) {
            return false;
        }
        System.arraycopy(this.lastValuea, 0, res, 0, this.lastValuea.length);
        return true;
    }

    @Override
    public void c2me$cache(int x, int y, int z, EvalType evalType, double cached) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastValue = cached;
        this.cacheSet = true;
    }

    @Override
    public void c2me$cache(double[] res, int[] x, int[] y, int[] z, EvalType evalType) {
        this.lastValuea = copyOrUpdate(this.lastValuea, res);
        this.lastXa = copyOrUpdate(this.lastXa, x);
        this.lastYa = copyOrUpdate(this.lastYa, y);
        this.lastZa = copyOrUpdate(this.lastZa, z);
    }

    @Override
    public DensityFunction c2me$getDelegate() {
        return this.delegate;
    }

    @Override
    public DensityFunction c2me$withDelegate(DensityFunction delegate) {
        this.delegate = Objects.requireNonNull(delegate);
        return this;
    }

    @Override
    public String c2me$getName() {
        return "RebuiltOnce";
    }

    @Override
    public boolean c2me$supportsMultiCache() {
        return true;
    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        return new RebuiltCacheOnce(this.delegate.apply(visitor));
    }

    @Override
    public double minValue() {
        return this.delegate.minValue();
    }

    @Override
    public double maxValue() {
        return this.delegate.maxValue();
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        throw new UnsupportedOperationException();
    }

    private static int[] copyOrUpdate(int[] current, int[] source) {
        if (current == null || current.length != source.length) {
            return Arrays.copyOf(source, source.length);
        }
        System.arraycopy(source, 0, current, 0, source.length);
        return current;
    }

    private static double[] copyOrUpdate(double[] current, double[] source) {
        if (current == null || current.length != source.length) {
            return Arrays.copyOf(source, source.length);
        }
        System.arraycopy(source, 0, current, 0, source.length);
        return current;
    }
}
