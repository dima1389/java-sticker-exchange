package com.stickerexchange.common.util;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public final class StickerSets {
    private StickerSets() {
    }

    public static SortedSet<Integer> normalize(SortedSet<Integer> values) {
        Objects.requireNonNull(values, "Sticker sets must not be null.");
        return Collections.unmodifiableSortedSet(new TreeSet<>(values));
    }
}
