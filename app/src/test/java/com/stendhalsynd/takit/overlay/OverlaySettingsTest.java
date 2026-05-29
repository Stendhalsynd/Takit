package com.stendhalsynd.takit.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OverlaySettingsTest {
    @Test
    public void progressMapsToAlphaRange() {
        assertEquals(0.35f, OverlaySettings.alphaFromProgress(0), 0.001f);
        assertEquals(0.675f, OverlaySettings.alphaFromProgress(50), 0.001f);
        assertEquals(1.0f, OverlaySettings.alphaFromProgress(100), 0.001f);
    }

    @Test
    public void alphaClampsOutsideRange() {
        assertEquals(0.35f, OverlaySettings.clampAlpha(0.1f), 0.001f);
        assertEquals(1.0f, OverlaySettings.clampAlpha(1.4f), 0.001f);
    }
}
