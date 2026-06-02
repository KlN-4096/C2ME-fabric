package com.ishland.c2me.opts.dfc.common.ast.opt.cache;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;

import java.util.IdentityHashMap;

public class Use {

    public static final class VisibleUses {
        private final IdentityHashMap<AstNode, UseCount> counts = new IdentityHashMap<>();

        void add(AstNode node, int count) {
            this.counts.computeIfAbsent(node, unused -> new UseCount()).totalUses += count;
        }

        void addAll(VisibleUses other) {
            for (var entry : other.counts.entrySet()) {
                add(entry.getKey(), entry.getValue().totalUses);
            }
        }

        void scale(int factor) {
            if (factor == 1) {
                return;
            }
            for (UseCount count : this.counts.values()) {
                count.totalUses *= factor;
            }
        }

        void scaleDiv(int divisor) {
            if (divisor <= 1) {
                return;
            }
            for (UseCount count : this.counts.values()) {
                count.totalUses = Math.max(1, count.totalUses / divisor);
            }
        }

        static VisibleUses max(VisibleUses a, VisibleUses b) {
            VisibleUses result = new VisibleUses();
            for (var entry : a.counts.entrySet()) {
                int bUses = b.count(entry.getKey());
                result.add(entry.getKey(), Math.max(entry.getValue().totalUses, bUses));
            }
            for (var entry : b.counts.entrySet()) {
                if (!a.counts.containsKey(entry.getKey())) {
                    result.add(entry.getKey(), entry.getValue().totalUses);
                }
            }
            return result;
        }

        private int count(AstNode node) {
            UseCount count = this.counts.get(node);
            return count != null ? count.totalUses : 0;
        }
    }

    public static final class UseCount {
        private int totalUses;
    }

    public static final class RegionUses {
        final IdentityHashMap<AstNode, BoundaryUse> boundaryUses;
        private final IdentityHashMap<AstNode, Boolean> selectedCache2DNodes = new IdentityHashMap<>();
        private final IdentityHashMap<AstNode, UseCount> visibleUses = new IdentityHashMap<>();
        private final IdentityHashMap<AstNode, VisibleUses> missBodyUsesByRoot = new IdentityHashMap<>();

        RegionUses(IdentityHashMap<AstNode, BoundaryUse> boundaryUses) {
            this.boundaryUses = boundaryUses;
        }

        void replaceVisibleUses(VisibleUses uses) {
            this.visibleUses.clear();
            this.visibleUses.putAll(uses.counts);
        }

        void replaceMissBodyUses(IdentityHashMap<AstNode, VisibleUses> byRoot) {
            this.missBodyUsesByRoot.clear();
            this.missBodyUsesByRoot.putAll(byRoot);
        }

        void replaceSelectedCache2DNodes(IdentityHashMap<AstNode, Boolean> selected) {
            this.selectedCache2DNodes.clear();
            this.selectedCache2DNodes.putAll(selected);
        }

        boolean isSelectedCache2D(AstNode node) {
            return this.selectedCache2DNodes.containsKey(node);
        }

        int visibleUseCount(AstNode node) {
            UseCount count = this.visibleUses.get(node);
            return count != null ? count.totalUses : 0;
        }

        int missBodyUseCount(AstNode node, AstNode covering2DRoot) {
            VisibleUses uses = this.missBodyUsesByRoot.get(covering2DRoot);
            return uses != null ? uses.count(node) : 0;
        }
    }

    public static final class BoundaryUse {
        private boolean outsideBoundary;
        private boolean insideBoundary;

        boolean mark(boolean insideBoundary) {
            if (insideBoundary) {
                boolean changed = !this.insideBoundary;
                this.insideBoundary = true;
                return changed;
            } else {
                boolean changed = !this.outsideBoundary;
                this.outsideBoundary = true;
                return changed;
            }
        }

        boolean crossesBoundary() {
            return this.outsideBoundary && this.insideBoundary;
        }
    }
}
