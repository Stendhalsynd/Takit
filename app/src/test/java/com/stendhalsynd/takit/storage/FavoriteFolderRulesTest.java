package com.stendhalsynd.takit.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

public class FavoriteFolderRulesTest {
    @Test
    public void toggleAddsMissingFolder() {
        Set<String> favorites = new LinkedHashSet<>();
        GalleryFolder folder = GalleryFolder.fromRelativePath("DCIM/Camera/");

        Set<String> updated = FavoriteFolderRules.toggle(favorites, folder);

        assertTrue(updated.contains("DCIM/Camera/"));
    }

    @Test
    public void toggleRemovesExistingFolder() {
        Set<String> favorites = new LinkedHashSet<>();
        favorites.add("DCIM/Camera/");
        GalleryFolder folder = GalleryFolder.fromRelativePath("DCIM/Camera/");

        Set<String> updated = FavoriteFolderRules.toggle(favorites, folder);

        assertFalse(updated.contains("DCIM/Camera/"));
    }
}
