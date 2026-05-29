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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FolderRepository {
    private static final String PREFS = "takit_folders";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_SELECTED = "selected";

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
        Set<String> paths = new LinkedHashSet<>(preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()));
        List<GalleryFolder> folders = new ArrayList<>();
        for (String path : paths) {
            folders.add(GalleryFolder.fromRelativePath(path));
        }
        folders.sort((left, right) -> left.getDisplayName().compareToIgnoreCase(right.getDisplayName()));
        return folders;
    }

    public GalleryFolder addFolder(String displayName) {
        GalleryFolder folder = GalleryFolder.fromDisplayName(displayName);
        saveSelectedFolder(folder);
        return folder;
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
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = {
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };
        Set<String> imported = new LinkedHashSet<>(preferences.getStringSet(KEY_FOLDERS, Collections.emptySet()));
        int before = imported.size();
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return 0;
            }
            int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                String path = cursor.getString(pathColumn);
                if (GalleryFolder.isSupportedGalleryPath(path)) {
                    imported.add(GalleryFolder.fromRelativePath(path).getRelativePath());
                }
            }
        }
        preferences.edit().putStringSet(KEY_FOLDERS, imported).apply();
        return imported.size() - before;
    }

    public int importExistingPictureFolders() {
        return importExistingGalleryFolders();
    }

    private void seedDefaults() {
        if (preferences.contains(KEY_FOLDERS)) {
            return;
        }
        Set<String> folders = new LinkedHashSet<>();
        folders.add(GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath());
        folders.add(GalleryFolder.fromDisplayName("Screenshots").getRelativePath());
        folders.add(GalleryFolder.fromDisplayName("Camera").getRelativePath());
        preferences.edit()
                .putStringSet(KEY_FOLDERS, folders)
                .putString(KEY_SELECTED, GalleryFolder.fromDisplayName(GalleryFolder.DEFAULT_NAME).getRelativePath())
                .apply();
    }
}
