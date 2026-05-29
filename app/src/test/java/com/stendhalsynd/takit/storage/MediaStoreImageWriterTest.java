package com.stendhalsynd.takit.storage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaStoreImageWriterTest {
    @Test
    public void buildDisplayNameIncludesMillisecondsAndSequence() {
        String name = MediaStoreImageWriter.buildDisplayName("TAKIT_CAMERA", ".jpg", 1710000000123L, 7);

        assertEquals("TAKIT_CAMERA_20240309_160000_123_007.jpg", name);
    }

    @Test
    public void buildDisplayNameNormalizesExtensionDot() {
        String name = MediaStoreImageWriter.buildDisplayName("TAKIT_SCREENSHOT", "png", 1710000000123L, 8);

        assertEquals("TAKIT_SCREENSHOT_20240309_160000_123_008.png", name);
    }
}
