package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.CacheLikeNode;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenCustomHashMap;

final class HashConsing {
    private HashConsing() {
    }

    static AstNode deduplicate(AstNode node) {
        return deduplicate(node, new Object2ReferenceOpenCustomHashMap<>(AstNodeIdentityStrategy.STRICT));
    }

    private static AstNode deduplicate(AstNode node, Object2ReferenceOpenCustomHashMap<AstNode, AstNode> nodes) {
        AstNode transformed = node.transform(astNode -> {
            if (astNode instanceof CacheLikeNode cacheLikeNode && cacheLikeNode.hasSideEffects()) {
                return astNode;
            }
            return intern(astNode, nodes);
        });
        return intern(transformed, nodes);
    }

    private static AstNode intern(AstNode node, Object2ReferenceOpenCustomHashMap<AstNode, AstNode> nodes) {
        AstNode existing = nodes.get(node);
        if (existing != null) {
            return existing;
        }
        nodes.put(node, node);
        return node;
    }
}
