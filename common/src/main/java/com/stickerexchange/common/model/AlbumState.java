package com.stickerexchange.common.model;

import com.stickerexchange.common.util.StickerSets;
import java.io.Serializable;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

public record AlbumState(SortedSet<Integer> duplicates, SortedSet<Integer> missing) implements Serializable {
    public static final int MIN_STICKER = 1;
    public static final int MAX_STICKER = 99;

    public AlbumState {
        duplicates = StickerSets.normalize(duplicates);
        missing = StickerSets.normalize(missing);
        validateRange(duplicates);
        validateRange(missing);
        validateNoOverlap(duplicates, missing);
    }

    public static AlbumState empty() {
        return new AlbumState(new TreeSet<>(), new TreeSet<>());
    }

    public static AlbumState of(Collection<Integer> duplicates, Collection<Integer> missing) {
        return new AlbumState(new TreeSet<>(duplicates), new TreeSet<>(missing));
    }

    public boolean duplicatesContainAll(Collection<Integer> stickers) {
        return duplicates.containsAll(stickers);
    }

    public boolean missingContainAll(Collection<Integer> stickers) {
        return missing.containsAll(stickers);
    }

    public AlbumState withoutDuplicates(Collection<Integer> removedDuplicates) {
        TreeSet<Integer> updatedDuplicates = new TreeSet<>(duplicates);
        updatedDuplicates.removeAll(removedDuplicates);
        return new AlbumState(updatedDuplicates, new TreeSet<>(missing));
    }

    public AlbumState withoutMissing(Collection<Integer> removedMissing) {
        TreeSet<Integer> updatedMissing = new TreeSet<>(missing);
        updatedMissing.removeAll(removedMissing);
        return new AlbumState(new TreeSet<>(duplicates), updatedMissing);
    }

    private static void validateRange(Collection<Integer> values) {
        for (Integer value : values) {
            if (value == null || value < MIN_STICKER || value > MAX_STICKER) {
                throw new IllegalArgumentException("Sticker numbers must stay in the range 1-99.");
            }
        }
    }

    private static void validateNoOverlap(Collection<Integer> duplicates, Collection<Integer> missing) {
        for (Integer value : duplicates) {
            if (missing.contains(value)) {
                throw new IllegalArgumentException("A sticker cannot be both a duplicate and missing.");
            }
        }
    }
}
