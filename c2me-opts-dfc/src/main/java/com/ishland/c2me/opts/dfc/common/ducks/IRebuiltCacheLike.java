package com.ishland.c2me.opts.dfc.common.ducks;

/**
 * Marker for cache wrappers inserted by DAG cache rebuild.
 */
public interface IRebuiltCacheLike {

    default boolean c2me$supportsMultiCache() {
        return false;
    }
}
