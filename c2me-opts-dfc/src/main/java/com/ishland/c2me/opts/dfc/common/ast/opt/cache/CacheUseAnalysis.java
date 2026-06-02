package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.BoundaryUse;
import com.ishland.c2me.opts.dfc.common.ast.opt.cache.Use.RegionUses;

import java.util.IdentityHashMap;

record CacheUseAnalysis(IdentityHashMap<AstNode, BoundaryUse> boundaryUses, RegionUses regionUses) {
}
