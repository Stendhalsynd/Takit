package com.stendhalsynd.takit.storage;

import java.util.Objects;

public final class GalleryFolder {
    public static final String DCIM_DIRECTORY = "DCIM";
    public static final String APP_ROOT = DCIM_DIRECTORY + "/Takit";
    public static final String DEFAULT_NAME = "Inbox";

    private final String displayName;
    private final String relativePath;

    private GalleryFolder(String displayName, String relativePath) {
        this.displayName = displayName;
        this.relativePath = relativePath;
    }

    public static GalleryFolder fromDisplayName(String rawName) {
        String displayName = sanitizeDisplayName(rawName);
        return new GalleryFolder(displayName, APP_ROOT + "/" + displayName + "/");
    }

    public static GalleryFolder fromRelativePath(String rawPath) {
        String relativePath = normalizeRelativePath(rawPath);
        String[] segments = relativePath.substring(0, relativePath.length() - 1).split("/");
        String displayName = segments.length == 0 ? DEFAULT_NAME : sanitizeDisplayName(segments[segments.length - 1]);
        return new GalleryFolder(displayName, relativePath);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public static String sanitizeDisplayName(String rawName) {
        if (rawName == null) {
            return DEFAULT_NAME;
        }
        String cleaned = rawName.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "-")
                .replaceAll("\\s+", " ")
                .replaceAll("-+", "-");
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return DEFAULT_NAME;
        }
        return cleaned;
    }

    private static String normalizeRelativePath(String rawPath) {
        String fallback = APP_ROOT + "/" + DEFAULT_NAME + "/";
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return fallback;
        }
        String cleaned = rawPath.trim().replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        cleaned = cleaned.replaceAll("/{2,}", "/");
        if (!cleaned.endsWith("/")) {
            cleaned = cleaned + "/";
        }
        if (!isSupportedGalleryPath(cleaned)) {
            return fallback;
        }
        return cleaned;
    }

    public static boolean isSupportedGalleryPath(String relativePath) {
        if (relativePath == null) {
            return false;
        }
        return relativePath.startsWith(DCIM_DIRECTORY + "/");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GalleryFolder)) {
            return false;
        }
        GalleryFolder that = (GalleryFolder) o;
        return relativePath.equals(that.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath);
    }
}
