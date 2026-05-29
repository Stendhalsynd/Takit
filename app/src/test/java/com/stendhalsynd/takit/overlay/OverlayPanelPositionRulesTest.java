package com.stendhalsynd.takit.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OverlayPanelPositionRulesTest {
    @Test
    public void capturesBubblePositionBeforePanelMovesToCenter() {
        OverlayPanelPositionRules.Position position = OverlayPanelPositionRules.captureReturnPosition(320, 640);

        assertEquals(320, position.getX());
        assertEquals(640, position.getY());
    }
}
