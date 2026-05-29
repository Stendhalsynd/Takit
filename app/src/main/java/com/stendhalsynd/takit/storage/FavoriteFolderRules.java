package com.stendhalsynd.takit.storage;

import java.util.LinkedHashSet;
import java.util.Set;

public final class FavoriteFolderRules {
    private FavoriteFolderRules() {
    }

    public static Set<String> toggle(Set<String> currentPaths, GalleryFolder folder) {
        Set<String> updated = new LinkedHashSet<>(currentPaths);
        String path = folder.getRelativePath();
        if (updated.contains(path)) {
            updated.remove(path);
        } else {
            updated.add(path);
        }
        return updated;
    }
}
