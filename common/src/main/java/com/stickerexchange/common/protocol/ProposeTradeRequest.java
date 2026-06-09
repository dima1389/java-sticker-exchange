package com.stickerexchange.common.protocol;

import com.stickerexchange.common.util.StickerSets;
import java.util.SortedSet;

public record ProposeTradeRequest(
        String targetUsername,
        SortedSet<Integer> requesterOffer,
        SortedSet<Integer> recipientOfferCandidates,
        boolean recipientSelectionRequired,
        int expectedRecipientSelectionSize) implements Message {

    public ProposeTradeRequest {
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new IllegalArgumentException("Target username is required.");
        }
        requesterOffer = StickerSets.normalize(requesterOffer);
        recipientOfferCandidates = StickerSets.normalize(recipientOfferCandidates);
        if (requesterOffer.isEmpty() || recipientOfferCandidates.isEmpty()) {
            throw new IllegalArgumentException("Trade requests need stickers on both sides.");
        }
        if (expectedRecipientSelectionSize <= 0) {
            throw new IllegalArgumentException("Expected selection size must be positive.");
        }
    }
}
