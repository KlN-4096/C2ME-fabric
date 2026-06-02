package com.ishland.c2me.opts.dfc.common.ast.misc;

import com.ishland.c2me.opts.dfc.common.ast.opt.cache.RebuiltCache2D;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.ducks.IRebuiltCacheLike;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;

record CacheTraits(
        CacheKind kind,
        boolean rebuilt,
        boolean rebuildable,
        boolean supportsMultiCache
) {

    static CacheTraits of(IFastCacheLike cacheLike) {
        if (cacheLike == null) {
            return new CacheTraits(CacheKind.NONE, false, false, true);
        }

        if (cacheLike instanceof IRebuiltCacheLike rebuiltCacheLike) {
            return new CacheTraits(
                    rebuiltKind(cacheLike),
                    true,
                    true,
                    rebuiltCacheLike.c2me$supportsMultiCache()
            );
        }

        if ((Object) cacheLike instanceof ChunkNoiseSampler.CacheOnce) {
            return new CacheTraits(CacheKind.CACHE_ONCE, false, true, true);
        }
        if ((Object) cacheLike instanceof ChunkNoiseSampler.Cache2D) {
            return new CacheTraits(CacheKind.CACHE_2D, false, true, true);
        }
        if ((Object) cacheLike instanceof ChunkNoiseSampler.FlatCache) {
            return new CacheTraits(CacheKind.FLAT_CACHE, false, false, true);
        }
        if ((Object) cacheLike instanceof DensityFunctionTypes.Wrapper wrapper) {
            return fromWrapper(wrapper.type());
        }

        return new CacheTraits(CacheKind.OTHER, false, false, true);
    }

    private static CacheKind rebuiltKind(IFastCacheLike cacheLike) {
        if (cacheLike instanceof RebuiltCache2D) {
            return CacheKind.CACHE_2D;
        }
        return CacheKind.CACHE_ONCE;
    }

    private static CacheTraits fromWrapper(DensityFunctionTypes.Wrapping.Type type) {
        return switch (type) {
            case CACHE_ONCE -> new CacheTraits(CacheKind.CACHE_ONCE, false, true, true);
            case CACHE2D -> new CacheTraits(CacheKind.CACHE_2D, false, true, true);
            case FLAT_CACHE -> new CacheTraits(CacheKind.FLAT_CACHE, false, false, true);
            default -> new CacheTraits(CacheKind.OTHER, false, false, true);
        };
    }

    boolean hasCache() {
        return this.kind != CacheKind.NONE;
    }

    boolean hasSideEffects() {
        return this.hasCache() && !this.rebuildable;
    }

    boolean blocksCacheInsertion() {
        return this.hasSideEffects() && !this.isFlat();
    }

    boolean isYIndependent() {
        return this.kind == CacheKind.CACHE_2D || this.kind == CacheKind.FLAT_CACHE;
    }

    boolean is2D() {
        return this.kind == CacheKind.CACHE_2D;
    }

    boolean isFlat() {
        return this.kind == CacheKind.FLAT_CACHE;
    }
}
