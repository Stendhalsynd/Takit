package com.stendhalsynd.takit.storage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GalleryFolderTest {
    @Test
    public void fromDisplayNameBuildsPicturesRelativePath() {
        GalleryFolder folder = GalleryFolder.fromDisplayName("Receipts 2026");

        assertEquals("Receipts 2026", folder.getDisplayName());
        assertEquals("Pictures/Takit/Receipts 2026/", folder.getRelativePath());
    }

    @Test
    public void fromDisplayNameSanitizesPathSeparators() {
        GalleryFolder folder = GalleryFolder.fromDisplayName("Travel/Seoul:May");

        assertEquals("Travel-Seoul-May", folder.getDisplayName());
        assertEquals("Pictures/Takit/Travel-Seoul-May/", folder.getRelativePath());
    }

    @Test
    public void blankDisplayNameFallsBackToInbox() {
        GalleryFolder folder = GalleryFolder.fromDisplayName("   ");

        assertEquals("Inbox", folder.getDisplayName());
        assertEquals("Pictures/Takit/Inbox/", folder.getRelativePath());
    }

    @Test
    public void fromRelativePathUsesFinalFolderAsDisplayName() {
        GalleryFolder folder = GalleryFolder.fromRelativePath("Pictures/Trips/Seoul/");

        assertEquals("Seoul", folder.getDisplayName());
        assertEquals("Pictures/Trips/Seoul/", folder.getRelativePath());
    }

    @Test
    public void fromRelativePathRejectsNonPictureRoots() {
        GalleryFolder folder = GalleryFolder.fromRelativePath("Download/Receipts/");

        assertEquals("Inbox", folder.getDisplayName());
        assertEquals("Pictures/Takit/Inbox/", folder.getRelativePath());
    }
}
