package com.stendhalsynd.takit.storage;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FolderRepository {
    private static final String PREFS = "takit_folders";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_FOLDER_METADATA = "folder_metadata";

    private final SharedPreferences preferences;
    private final Context context;

    public FolderRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedDefaults();
    }

    public GalleryFolder getSelectedFolder() {
        String selectedPath = preferences.getString(KEY_SELECTED, null);
        if (selectedPath == null) {
            GalleryFolder inbox = GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME);
            saveSelectedFolder(inbox);
            return inbox;
        }
        return GalleryFolder.fromRelativePath(selectedPath);
    }

    public void saveSelectedFolder(GalleryFolder folder) {
        Set<String> folders = new LinkedHashSet<>(preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()));
        folders.add(folder.getRelativePath());
        preferences.edit()
                .putStringSet(KEY_FOLDERS, folders)
                .putString(KEY_SELECTED, folder.getRelativePath())
                .apply();
    }

    public List<GalleryFolder> getFolders() {
        List<FolderListItem> items = getFolderItems();
        List<GalleryFolder> folders = new ArrayList<>();
        for (FolderListItem item : items) {
            folders.add(item.getFolder());
        }
        return folders;
    }

    public List<FolderListItem> getFolderItems() {
        Set<String> paths = new LinkedHashSet<>(preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()));
        Map<String, FolderMetadata> metadata = readMetadata();
        List<FolderListItem> folders = new ArrayList<>();
        Set<String> normalizedPaths = new LinkedHashSet<>();
        for (String path : paths) {
            if (!GalleryFolder.isSupportedGalleryPath(path)) {
                continue;
            }
            GalleryFolder folder = GalleryFolder.fromRelativePath(path);
            if (!normalizedPaths.add(folder.getRelativePath())) {
                continue;
            }
            FolderMetadata folderMetadata = metadata.get(folder.getRelativePath());
            folders.add(new FolderListItem(
                    folder,
                    folderMetadata == null ? 0 : folderMetadata.itemCount,
                    folderMetadata == null ? 0L : folderMetadata.latestModifiedSeconds
            ));
        }
        return FolderListRules.sort(folders, FolderListSort.NAME, FolderListDirection.ASCENDING);
    }

    public List<GalleryFolder> getFavoriteFolders() {
        Set<String> favoritePaths = new LinkedHashSet<>(preferences.getStringSet(KEY_FAVORITES, Collections.emptySet()));
        List<GalleryFolder> favorites = new ArrayList<>();
        Set<String> normalizedPaths = new LinkedHashSet<>();
        for (String path : favoritePaths) {
            if (!GalleryFolder.isSupportedGalleryPath(path)) {
                continue;
            }
            GalleryFolder folder = GalleryFolder.fromRelativePath(path);
            if (normalizedPaths.add(folder.getRelativePath())) {
                favorites.add(folder);
            }
        }
        favorites.sort((left, right) -> left.getDisplayName().compareToIgnoreCase(right.getDisplayName()));
        return favorites;
    }

    public GalleryFolder addFolder(String displayName) {
        GalleryFolder folder = GalleryFolder.fromDisplayName(displayName);
        saveSelectedFolder(folder);
        return folder;
    }

    public boolean isFavorite(GalleryFolder folder) {
        Set<String> favorites = preferences.getStringSet(KEY_FAVORITES, Collections.emptySet());
        return favorites.contains(folder.getRelativePath());
    }

    public void toggleFavorite(GalleryFolder folder) {
        Set<String> favorites = FavoriteFolderRules.toggle(
                preferences.getStringSet(KEY_FAVORITES, Collections.emptySet()),
                folder
        );
        preferences.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public boolean canReadMediaFolders() {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public int importExistingGalleryFolders() {
        if (!canReadMediaFolders()) {
            return 0;
        }
        List<FolderListItem> snapshot = readDcimSnapshot();
        Set<String> previous = new LinkedHashSet<>(preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()));
        syncDcimSnapshot(snapshot);
        return Math.max(0, preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()).size() - previous.size());
    }

    public int syncExistingGalleryFolders() {
        if (!canReadMediaFolders()) {
            return 0;
        }
        List<FolderListItem> snapshot = readDcimSnapshot();
        syncDcimSnapshot(snapshot);
        return snapshot.size();
    }

    public int importExistingPictureFolders() {
        return importExistingGalleryFolders();
    }

    private List<FolderListItem> readDcimSnapshot() {
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = {
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_MODIFIED
        };
        Map<String, FolderMetadata> metadataByPath = new HashMap<>();
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return new ArrayList<>();
            }
            int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                String path = cursor.getString(pathColumn);
                if (GalleryFolder.isSupportedGalleryPath(path)) {
                    GalleryFolder folder = GalleryFolder.fromRelativePath(path);
                    String relativePath = folder.getRelativePath();
                    FolderMetadata metadata = metadataByPath.get(relativePath);
                    long modified = Math.max(0L, cursor.getLong(modifiedColumn));
                    if (metadata == null) {
                        metadataByPath.put(relativePath, new FolderMetadata(1, modified));
                    } else {
                        metadataByPath.put(relativePath, new FolderMetadata(
                                metadata.itemCount + 1,
                                Math.max(metadata.latestModifiedSeconds, modified)
                        ));
                    }
                }
            }
        }
        List<FolderListItem> snapshot = new ArrayList<>();
        for (Map.Entry<String, FolderMetadata> entry : metadataByPath.entrySet()) {
            snapshot.add(new FolderListItem(
                    GalleryFolder.fromRelativePath(entry.getKey()),
                    entry.getValue().itemCount,
                    entry.getValue().latestModifiedSeconds
            ));
        }
        return snapshot;
    }

    private void syncDcimSnapshot(List<FolderListItem> snapshot) {
        Set<String> currentPaths = new LinkedHashSet<>(preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()));
        Set<String> syncedPaths = FolderListRules.syncDcimPaths(currentPaths, snapshot);
        Set<String> favoritePaths = FolderListRules.retainAvailableFavorites(
                preferences.getStringSet(KEY_FAVORITES, Collections.emptySet()),
                syncedPaths
        );
        String selectedPath = FolderListRules.resolveSelectedPath(
                preferences.getString(KEY_SELECTED, GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath()),
                syncedPaths
        );
        preferences.edit()
                .putStringSet(KEY_FOLDERS, syncedPaths)
                .putStringSet(KEY_FAVORITES, favoritePaths)
                .putStringSet(KEY_FOLDER_METADATA, serializeMetadata(snapshot))
                .putString(KEY_SELECTED, selectedPath)
                .apply();
    }

    private Set<String> serializeMetadata(List<FolderListItem> snapshot) {
        Set<String> serialized = new LinkedHashSet<>();
        for (FolderListItem item : snapshot) {
            serialized.add(item.getFolder().getRelativePath()
                    + "\t" + item.getItemCount()
                    + "\t" + item.getLatestModifiedSeconds());
        }
        return serialized;
    }

    private Map<String, FolderMetadata> readMetadata() {
        Set<String> serialized = preferences.getStringSet(KEY_FOLDER_METADATA, Collections.emptySet());
        Map<String, FolderMetadata> metadata = new HashMap<>();
        for (String value : serialized) {
            String[] parts = value.split("\t");
            if (parts.length != 3) {
                continue;
            }
            try {
                metadata.put(parts[0], new FolderMetadata(
                        Integer.parseInt(parts[1]),
                        Long.parseLong(parts[2])
                ));
            } catch (NumberFormatException ignored) {
                // Ignore corrupt local metadata and keep the folder visible without counts.
            }
        }
        return metadata;
    }

    private void seedDefaults() {
        if (preferences.contains(KEY_FOLDERS)) {
            return;
        }
        Set<String> folders = new LinkedHashSet<>();
        folders.add(GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath());
        folders.add(GalleryFolder.fromDisplayName("Screenshots").getRelativePath());
        folders.add(GalleryFolder.fromDisplayName("Camera").getRelativePath());
        Set<String> favorites = new LinkedHashSet<>();
        favorites.add(GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath());
        preferences.edit()
                .putStringSet(KEY_FOLDERS, folders)
                .putStringSet(KEY_FAVORITES, favorites)
                .putString(KEY_SELECTED, GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath())
                .apply();
    }

    private static final class FolderMetadata {
        final int itemCount;
        final long latestModifiedSeconds;

        FolderMetadata(int itemCount, long latestModifiedSeconds) {
            this.itemCount = itemCount;
            this.latestModifiedSeconds = latestModifiedSeconds;
        }
    }
}
