package com.stickerexchange.common.service;

import com.stickerexchange.common.model.AlbumState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class AlbumInitializer {
    private static final int MIN_INITIAL_STICKERS = 6;
    private static final int MAX_INITIAL_STICKERS = 15;

    private final Random random;

    public AlbumInitializer() {
        this(new Random());
    }

    public AlbumInitializer(Random random) {
        this.random = random;
    }

    public AlbumState createRandomAlbum() {
        List<Integer> allStickers = IntStream.rangeClosed(AlbumState.MIN_STICKER, AlbumState.MAX_STICKER)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(allStickers, random);

        int duplicateCount = randomCount();
        int missingCount = randomCount();
        TreeSet<Integer> duplicates = new TreeSet<>(allStickers.subList(0, duplicateCount));
        TreeSet<Integer> missing = new TreeSet<>(allStickers.subList(duplicateCount, duplicateCount + missingCount));
        return new AlbumState(duplicates, missing);
    }

    private int randomCount() {
        return random.nextInt(MIN_INITIAL_STICKERS, MAX_INITIAL_STICKERS + 1);
    }
}
