package com.stickerexchange.common.model;

import com.stickerexchange.common.util.StickerSets;
import java.io.Serializable;
import java.util.SortedSet;

public record TradeMatch(
        String otherUsername,
        SortedSet<Integer> offerFromCurrentUser,
        SortedSet<Integer> offerFromOtherUser) implements Serializable {

    public TradeMatch {
        if (otherUsername == null || otherUsername.isBlank()) {
            throw new IllegalArgumentException("Trade matches require a username.");
        }
        offerFromCurrentUser = StickerSets.normalize(offerFromCurrentUser);
        offerFromOtherUser = StickerSets.normalize(offerFromOtherUser);
        if (offerFromCurrentUser.isEmpty() || offerFromOtherUser.isEmpty()) {
            throw new IllegalArgumentException("Trade matches need stickers on both sides.");
        }
    }

    public boolean currentUserMustSelect() {
        return offerFromCurrentUser.size() > offerFromOtherUser.size();
    }

    public boolean otherUserMustSelect() {
        return offerFromOtherUser.size() > offerFromCurrentUser.size();
    }

    public int tradeSize() {
        return Math.min(offerFromCurrentUser.size(), offerFromOtherUser.size());
    }

    @Override
    public String toString() {
        return otherUsername + " (" + tradeSize() + " stickers)";
    }
}
