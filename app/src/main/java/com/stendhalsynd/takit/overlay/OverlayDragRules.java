package com.stendhalsynd.takit.overlay;

public final class OverlayDragRules {
    private OverlayDragRules() {
    }

    public static boolean isInsideDismissCircle(
            float bubbleCenterX,
            float bubbleCenterY,
            float circleCenterX,
            float circleCenterY,
            float radius
    ) {
        float deltaX = bubbleCenterX - circleCenterX;
        float deltaY = bubbleCenterY - circleCenterY;
        return deltaX * deltaX + deltaY * deltaY <= radius * radius;
    }
}
