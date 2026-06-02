package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import it.unimi.dsi.fastutil.Hash;

final class AstNodeIdentityStrategy {

    static final Hash.Strategy<AstNode> STRICT = new Hash.Strategy<>() {
        @Override
        public int hashCode(AstNode node) {
            return node.hashCode();
        }

        @Override
        public boolean equals(AstNode a, AstNode b) {
            return a.equals(b);
        }
    };

    private AstNodeIdentityStrategy() {
    }
}
