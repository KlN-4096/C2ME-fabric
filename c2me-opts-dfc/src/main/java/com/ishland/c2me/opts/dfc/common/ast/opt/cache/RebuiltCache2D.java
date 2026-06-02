package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.ducks.IRebuiltCacheLike;
import com.ishland.c2me.opts.dfc.common.vif.EachApplierVanillaInterface;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.Arrays;
import java.util.Objects;

public final class RebuiltCache2D implements IFastCacheLike, IRebuiltCacheLike {

    private DensityFunction delegate;
    private boolean cacheSet;
    private long lastColumn;
    private double lastValue;
    private long[] lastColumns;
    private double[] lastValuea;

    RebuiltCache2D(DensityFunction delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public double sample(NoisePos pos) {
        int x = pos.blockX();
        int z = pos.blockZ();
        double cached = this.c2me$getCached(x, pos.blockY(), z, EvalType.from(pos));
        if (Double.doubleToRawLongBits(cached) != CACHE_MISS_NAN_BITS) {
            return cached;
        }
        double sampled = this.delegate.sample(pos);
        this.c2me$cache(x, pos.blockY(), z, EvalType.from(pos), sampled);
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
        long column = ChunkPos.toLong(x, z);
        if (this.lastColumns != null) {
            for (int i = 0; i < this.lastColumns.length; i++) {
                if (this.lastColumns[i] == column) {
                    return this.lastValuea[i];
                }
            }
        }
        if (this.cacheSet && this.lastColumn == column) {
            return this.lastValue;
        }
        return Double.longBitsToDouble(CACHE_MISS_NAN_BITS);
    }

    @Override
    public boolean c2me$getCached(double[] res, int[] x, int[] y, int[] z, EvalType evalType) {
        if (this.lastValuea == null || this.lastValuea.length != res.length || this.lastColumns == null) {
            return false;
        }
        for (int i = 0; i < res.length; i++) {
            if (this.lastColumns[i] != ChunkPos.toLong(x[i], z[i])) {
                return false;
            }
        }
        System.arraycopy(this.lastValuea, 0, res, 0, this.lastValuea.length);
        return true;
    }

    @Override
    public void c2me$cache(int x, int y, int z, EvalType evalType, double cached) {
        this.lastColumn = ChunkPos.toLong(x, z);
        this.lastValue = cached;
        this.cacheSet = true;
    }

    @Override
    public void c2me$cache(double[] res, int[] x, int[] y, int[] z, EvalType evalType) {
        this.lastValuea = copyOrUpdate(this.lastValuea, res);
        if (this.lastColumns == null || this.lastColumns.length != res.length) {
            this.lastColumns = new long[res.length];
        }
        for (int i = 0; i < res.length; i++) {
            this.lastColumns[i] = ChunkPos.toLong(x[i], z[i]);
        }
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
        return "Rebuilt2D";
    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        return new RebuiltCache2D(this.delegate.apply(visitor));
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

    private static double[] copyOrUpdate(double[] current, double[] source) {
        if (current == null || current.length != source.length) {
            return Arrays.copyOf(source, source.length);
        }
        System.arraycopy(source, 0, current, 0, source.length);
        return current;
    }
}
