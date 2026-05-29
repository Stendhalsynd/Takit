package com.stendhalsynd.takit.overlay;

public final class OverlaySettings {
    public static final float MIN_ALPHA = 0.35f;
    public static final float MAX_ALPHA = 1.0f;
    public static final float DEFAULT_ALPHA = 0.88f;

    private OverlaySettings() {
    }

    public static float alphaFromProgress(int progress) {
        int clamped = Math.max(0, Math.min(100, progress));
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * (clamped / 100f);
    }

    public static int progressFromAlpha(float alpha) {
        float clamped = clampAlpha(alpha);
        return Math.round(((clamped - MIN_ALPHA) / (MAX_ALPHA - MIN_ALPHA)) * 100f);
    }

    public static float clampAlpha(float alpha) {
        return Math.max(MIN_ALPHA, Math.min(MAX_ALPHA, alpha));
    }
}
