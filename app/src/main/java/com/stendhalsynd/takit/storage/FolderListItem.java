package com.stendhalsynd.takit.storage;

public final class FolderListItem {
    private final GalleryFolder folder;
    private final int itemCount;
    private final long latestModifiedSeconds;

    public FolderListItem(GalleryFolder folder, int itemCount, long latestModifiedSeconds) {
        this.folder = folder;
        this.itemCount = Math.max(0, itemCount);
        this.latestModifiedSeconds = Math.max(0L, latestModifiedSeconds);
    }

    public GalleryFolder getFolder() {
        return folder;
    }

    public int getItemCount() {
        return itemCount;
    }

    public long getLatestModifiedSeconds() {
        return latestModifiedSeconds;
    }
}
