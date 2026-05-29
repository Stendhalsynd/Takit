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
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public final class MediaStoreImageWriter {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private MediaStoreImageWriter() {
    }

    public static Uri createPendingImage(ContentResolver resolver, GalleryFolder folder, String prefix, String extension, String mimeType) {
        return createImage(resolver, folder, prefix, extension, mimeType, true);
    }

    public static Uri createCameraOutputImage(ContentResolver resolver, GalleryFolder folder) {
        return createImage(resolver, folder, "TAKIT_CAMERA", ".jpg", "image/jpeg", false);
    }

    public static Uri createImage(ContentResolver resolver, GalleryFolder folder, String prefix, String extension, String mimeType, boolean pending) {
        long now = System.currentTimeMillis();
        int sequence = Math.floorMod(SEQUENCE.incrementAndGet(), 1000);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, buildDisplayName(prefix, extension, now, sequence));
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, folder.getRelativePath());
        values.put(MediaStore.Images.Media.IS_PENDING, pending ? 1 : 0);
        values.put(MediaStore.Images.Media.DATE_ADDED, now / 1000L);
        return resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
    }

    public static String buildDisplayName(String prefix, String extension, long timestampMillis, int sequence) {
        String normalizedExtension = extension.startsWith(".") ? extension : "." + extension;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = formatter.format(new Date(timestampMillis));
        return String.format(Locale.US, "%s_%s_%03d%s", prefix, timestamp, Math.floorMod(sequence, 1000), normalizedExtension);
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
