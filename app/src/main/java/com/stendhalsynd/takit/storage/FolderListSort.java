package com.stendhalsynd.takit.storage;

public enum FolderListSort {
    NAME("name"),
    MODIFIED("modified");

    private final String value;

    FolderListSort(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FolderListSort fromValue(String value) {
        if (MODIFIED.value.equals(value)) {
            return MODIFIED;
        }
        return NAME;
    }
}
