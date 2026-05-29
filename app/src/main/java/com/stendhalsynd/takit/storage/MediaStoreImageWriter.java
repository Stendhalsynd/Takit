package com.stendhalsynd.takit.storage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MediaStoreImageWriter {
    private MediaStoreImageWriter() {
    }

    public static Uri createPendingImage(ContentResolver resolver, GalleryFolder folder, String prefix, String extension, String mimeType) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, prefix + "_" + timestamp + extension);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, folder.getRelativePath());
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        return resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
    }

    public static Uri savePng(ContentResolver resolver, GalleryFolder folder, Bitmap bitmap, String prefix) throws IOException {
        Uri uri = createPendingImage(resolver, folder, prefix, ".png", "image/png");
        if (uri == null) {
            throw new IOException("MediaStore insert returned null");
        }
        try (OutputStream outputStream = resolver.openOutputStream(uri)) {
            if (outputStream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("Failed to write image data");
            }
        } catch (IOException error) {
            deleteQuietly(resolver, uri);
            throw error;
        }
        publish(resolver, uri);
        return uri;
    }

    public static void publish(ContentResolver resolver, Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
    }

    public static void deleteQuietly(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup for abandoned pending captures.
        }
    }
}
