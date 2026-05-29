package com.stendhalsynd.takit.capture;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScreenshotSessionRulesTest {
    @Test
    public void usesActiveSessionWhenProjectionIsReady() {
        assertEquals(
                ScreenshotSessionRules.NextStep.CAPTURE_WITH_ACTIVE_SESSION,
                ScreenshotSessionRules.nextStep(true)
        );
    }

    @Test
    public void requestsConsentWhenProjectionIsNotReady() {
        assertEquals(
                ScreenshotSessionRules.NextStep.REQUEST_CONSENT,
                ScreenshotSessionRules.nextStep(false)
        );
    }

    @Test
    public void clampsNegativeCaptureDelayToZero() {
        assertEquals(0L, ScreenshotSessionRules.sanitizedDelay(-250L));
        assertEquals(450L, ScreenshotSessionRules.sanitizedDelay(450L));
    }
}
