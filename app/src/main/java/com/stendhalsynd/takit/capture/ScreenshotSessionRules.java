package com.stendhalsynd.takit.capture;

public final class ScreenshotSessionRules {
    public enum NextStep {
        REQUEST_CONSENT,
        CAPTURE_WITH_ACTIVE_SESSION
    }

    private ScreenshotSessionRules() {
    }

    public static NextStep nextStep(boolean activeSessionReady) {
        return activeSessionReady
                ? NextStep.CAPTURE_WITH_ACTIVE_SESSION
                : NextStep.REQUEST_CONSENT;
    }

    public static long sanitizedDelay(long delayMs) {
        return Math.max(0L, delayMs);
    }
}
