package com.stickerexchange.common.service;

import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

public final class ExchangeCalculator {
    private ExchangeCalculator() {
    }

    public static Optional<TradeMatch> calculateMatch(String otherUsername, AlbumState currentAlbum, AlbumState otherAlbum) {
        SortedSet<Integer> offerFromCurrentUser = intersection(currentAlbum.duplicates(), otherAlbum.missing());
        SortedSet<Integer> offerFromOtherUser = intersection(otherAlbum.duplicates(), currentAlbum.missing());
        if (offerFromCurrentUser.isEmpty() || offerFromOtherUser.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TradeMatch(otherUsername, offerFromCurrentUser, offerFromOtherUser));
    }

    public static List<TradeMatch> calculateMatches(AlbumState currentAlbum, Map<String, AlbumState> otherAlbums) {
        return otherAlbums.entrySet().stream()
                .map(entry -> calculateMatch(entry.getKey(), currentAlbum, entry.getValue()))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(TradeMatch::otherUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static SortedSet<Integer> intersection(SortedSet<Integer> left, SortedSet<Integer> right) {
        TreeSet<Integer> result = new TreeSet<>(left);
        result.retainAll(right);
        return result;
    }
}
