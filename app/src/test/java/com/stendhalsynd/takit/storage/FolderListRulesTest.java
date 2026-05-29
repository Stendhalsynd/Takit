package com.stendhalsynd.takit.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FolderListRulesTest {
    @Test
    public void syncSnapshotKeepsDcimAndDropsPicturesAndStaleImportedFolders() {
        Set<String> existing = new LinkedHashSet<>(Arrays.asList(
                "DCIM/Takit/Inbox/",
                "DCIM/Old Trip/",
                "Pictures/Trips/"
        ));
        List<FolderListItem> snapshot = Arrays.asList(
                item("Pictures/Trips/", 4, 30),
                item("DCIM/Camera/", 8, 40),
                item("DCIM/New Trip/", 2, 20)
        );

        Set<String> synced = FolderListRules.syncDcimPaths(existing, snapshot);

        assertTrue(synced.contains("DCIM/Takit/Inbox/"));
        assertTrue(synced.contains("DCIM/Camera/"));
        assertTrue(synced.contains("DCIM/New Trip/"));
        assertFalse(synced.contains("DCIM/Old Trip/"));
        assertFalse(synced.contains("Pictures/Trips/"));
    }

    @Test
    public void filterSearchesInsideDcimRelativeFolderTextOnly() {
        List<FolderListItem> folders = Arrays.asList(
                item("DCIM/Camera Raw/", 9, 30),
                item("DCIM/Receipts/", 3, 20),
                item("Pictures/Camera Export/", 7, 10)
        );

        List<FolderListItem> filtered = FolderListRules.filter(folders, "camera");

        assertEquals(1, filtered.size());
        assertEquals("DCIM/Camera Raw/", filtered.get(0).getFolder().getRelativePath());
    }

    @Test
    public void sortsByNameOrModifiedWithDirection() {
        List<FolderListItem> folders = Arrays.asList(
                item("DCIM/Beta/", 2, 10),
                item("DCIM/Alpha/", 5, 30),
                item("DCIM/Gamma/", 1, 20)
        );

        List<FolderListItem> byName = FolderListRules.sort(
                folders,
                FolderListSort.NAME,
                FolderListDirection.ASCENDING
        );
        List<FolderListItem> byModified = FolderListRules.sort(
                folders,
                FolderListSort.MODIFIED,
                FolderListDirection.DESCENDING
        );

        assertEquals("Alpha", byName.get(0).getFolder().getDisplayName());
        assertEquals("Gamma", byName.get(2).getFolder().getDisplayName());
        assertEquals("Alpha", byModified.get(0).getFolder().getDisplayName());
        assertEquals("Beta", byModified.get(2).getFolder().getDisplayName());
    }

    @Test
    public void paginatesWithClampedPageIndex() {
        List<FolderListItem> folders = Arrays.asList(
                item("DCIM/One/", 1, 1),
                item("DCIM/Two/", 1, 1),
                item("DCIM/Three/", 1, 1)
        );

        List<FolderListItem> secondPage = FolderListRules.page(folders, 1, 2);
        List<FolderListItem> outOfRangePage = FolderListRules.page(folders, 9, 2);

        assertEquals(1, secondPage.size());
        assertEquals("Three", secondPage.get(0).getFolder().getDisplayName());
        assertEquals(1, outOfRangePage.size());
        assertEquals(1, FolderListRules.maxPageIndex(3, 2));
    }

    @Test
    public void removesMissingFavoritesAndFallsBackSelectedFolder() {
        Set<String> availablePaths = new LinkedHashSet<>(Arrays.asList(
                "DCIM/Takit/Inbox/",
                "DCIM/Camera/"
        ));
        Set<String> favorites = new LinkedHashSet<>(Arrays.asList(
                "DCIM/Camera/",
                "DCIM/Missing/"
        ));

        Set<String> cleanedFavorites = FolderListRules.retainAvailableFavorites(favorites, availablePaths);
        String selected = FolderListRules.resolveSelectedPath("DCIM/Missing/", availablePaths);

        assertTrue(cleanedFavorites.contains("DCIM/Camera/"));
        assertFalse(cleanedFavorites.contains("DCIM/Missing/"));
        assertEquals("DCIM/Takit/Inbox/", selected);
    }

    private static FolderListItem item(String path, int count, long modified) {
        return new FolderListItem(GalleryFolder.fromRelativePath(path), count, modified);
    }
}
