package com.stendhalsynd.takit.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayDragRulesTest {
    @Test
    public void detectsBubbleCenterInsideDismissCircle() {
        assertTrue(OverlayDragRules.isInsideDismissCircle(540f, 1820f, 540f, 1800f, 96f));
        assertFalse(OverlayDragRules.isInsideDismissCircle(300f, 1820f, 540f, 1800f, 96f));
    }

    @Test
    public void dismissContainerMustLeaveRoomForSelectedScale() {
        assertTrue(OverlayDragRules.hasUnclippedScaledCircle(160f, 104f, 1.12f));
        assertFalse(OverlayDragRules.hasUnclippedScaledCircle(104f, 104f, 1.12f));
    }
}
