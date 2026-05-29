package com.stendhalsynd.takit.storage;

public enum FolderListDirection {
    ASCENDING("asc"),
    DESCENDING("desc");

    private final String value;

    FolderListDirection(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FolderListDirection fromValue(String value) {
        if (DESCENDING.value.equals(value)) {
            return DESCENDING;
        }
        return ASCENDING;
    }
}
