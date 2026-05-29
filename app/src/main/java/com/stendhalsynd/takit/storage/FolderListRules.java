package com.stendhalsynd.takit.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FolderListRules {
    private static final int MIN_PAGE_SIZE = 1;

    private FolderListRules() {
    }

    public static Set<String> syncDcimPaths(Set<String> existingPaths, List<FolderListItem> snapshot) {
        Set<String> synced = new LinkedHashSet<>();
        synced.add(GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath());
        for (String path : existingPaths) {
            if (isAppManagedPath(path)) {
                synced.add(GalleryFolder.fromRelativePath(path).getRelativePath());
            }
        }
        for (FolderListItem item : snapshot) {
            String path = item.getFolder().getRelativePath();
            if (GalleryFolder.isSupportedGalleryPath(path)) {
                synced.add(path);
            }
        }
        return synced;
    }

    public static List<FolderListItem> filter(List<FolderListItem> folders, String rawQuery) {
        String query = normalize(rawQuery);
        List<FolderListItem> filtered = new ArrayList<>();
        for (FolderListItem item : folders) {
            String path = item.getFolder().getRelativePath();
            if (!GalleryFolder.isSupportedGalleryPath(path)) {
                continue;
            }
            if (query.isEmpty() || searchableText(item).contains(query)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public static List<FolderListItem> sort(List<FolderListItem> folders, FolderListSort sort, FolderListDirection direction) {
        List<FolderListItem> sorted = new ArrayList<>(folders);
        Comparator<FolderListItem> comparator;
        if (sort == FolderListSort.MODIFIED) {
            comparator = Comparator
                    .comparingLong(FolderListItem::getLatestModifiedSeconds)
                    .thenComparing(item -> item.getFolder().getDisplayName(), String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator
                    .comparing((FolderListItem item) -> item.getFolder().getDisplayName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(item -> item.getFolder().getRelativePath(), String.CASE_INSENSITIVE_ORDER);
        }
        if (direction == FolderListDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator);
        return sorted;
    }

    public static List<FolderListItem> page(List<FolderListItem> folders, int pageIndex, int pageSize) {
        int safePageSize = Math.max(MIN_PAGE_SIZE, pageSize);
        int safePageIndex = Math.max(0, Math.min(pageIndex, maxPageIndex(folders.size(), safePageSize)));
        int fromIndex = safePageIndex * safePageSize;
        int toIndex = Math.min(folders.size(), fromIndex + safePageSize);
        return new ArrayList<>(folders.subList(fromIndex, toIndex));
    }

    public static int maxPageIndex(int itemCount, int pageSize) {
        int safePageSize = Math.max(MIN_PAGE_SIZE, pageSize);
        if (itemCount <= 0) {
            return 0;
        }
        return (itemCount - 1) / safePageSize;
    }

    public static int stablePageIndex(int currentPageIndex, int itemCount, int pageSize) {
        return Math.max(0, Math.min(currentPageIndex, maxPageIndex(itemCount, pageSize)));
    }

    public static Set<String> retainAvailableFavorites(Set<String> favorites, Set<String> availablePaths) {
        Set<String> retained = new LinkedHashSet<>();
        for (String path : favorites) {
            if (availablePaths.contains(path)) {
                retained.add(path);
            }
        }
        return retained;
    }

    public static String resolveSelectedPath(String selectedPath, Set<String> availablePaths) {
        if (availablePaths.contains(selectedPath)) {
            return selectedPath;
        }
        String defaultPath = GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath();
        if (availablePaths.contains(defaultPath)) {
            return defaultPath;
        }
        if (!availablePaths.isEmpty()) {
            return availablePaths.iterator().next();
        }
        return defaultPath;
    }

    public static boolean isAppManagedPath(String path) {
        return path != null && path.startsWith(GalleryFolder.APP_ROOT + "/");
    }

    private static String searchableText(FolderListItem item) {
        String path = item.getFolder().getRelativePath();
        String dcimRelative = path.startsWith(GalleryFolder.DCIM_DIRECTORY + "/")
                ? path.substring((GalleryFolder.DCIM_DIRECTORY + "/").length())
                : path;
        return normalize(item.getFolder().getDisplayName() + " " + dcimRelative);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
