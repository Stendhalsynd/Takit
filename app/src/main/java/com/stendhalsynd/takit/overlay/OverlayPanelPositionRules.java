package com.stendhalsynd.takit.overlay;

public final class OverlayPanelPositionRules {
    private OverlayPanelPositionRules() {
    }

    public static Position captureReturnPosition(int x, int y) {
        return new Position(x, y);
    }

    public static final class Position {
        private final int x;
        private final int y;

        private Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
