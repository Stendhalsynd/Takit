package com.stendhalsynd.takit.storage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GalleryFolderTest {
    @Test
    public void fromDisplayNameBuildsDcimRelativePath() {
        GalleryFolder folder = GalleryFolder.fromDisplayName("Receipts 2026");

        assertEquals("Receipts 2026", folder.getDisplayName());
        assertEquals("DCIM/Takit/Receipts 2026/", folder.getRelativePath());
    }

    @Test
    public void fromDisplayNameSanitizesPathSeparators() {
        GalleryFolder folder = GalleryFolder.fromDisplayName("Travel/Seoul:May");

        assertEquals("Travel-Seoul-May", folder.getDisplayName());
        assertEquals("DCIM/Takit/Travel-Seoul-May/", folder.getRelativePath());
    }

    @Test
    public void blankDisplayNameFallsBackToInbox() {
        GalleryFolder folder = GalleryFolder.fromDisplayName("   ");

        assertEquals("Inbox", folder.getDisplayName());
        assertEquals("DCIM/Takit/Inbox/", folder.getRelativePath());
    }

    @Test
    public void fromRelativePathRejectsPicturesFoldersByDefault() {
        GalleryFolder folder = GalleryFolder.fromRelativePath("Pictures/Trips/Seoul/");

        assertEquals("Inbox", folder.getDisplayName());
        assertEquals("DCIM/Takit/Inbox/", folder.getRelativePath());
    }

    @Test
    public void fromRelativePathAcceptsDcimCameraAlbums() {
        GalleryFolder folder = GalleryFolder.fromRelativePath("DCIM/Camera/");

        assertEquals("Camera", folder.getDisplayName());
        assertEquals("DCIM/Camera/", folder.getRelativePath());
    }

    @Test
    public void fromRelativePathRejectsNonGalleryRoots() {
        GalleryFolder folder = GalleryFolder.fromRelativePath("Download/Receipts/");

        assertEquals("Inbox", folder.getDisplayName());
        assertEquals("DCIM/Takit/Inbox/", folder.getRelativePath());
    }
}
