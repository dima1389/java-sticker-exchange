package com.stickerexchange.common.model;

import com.stickerexchange.common.util.StickerSets;
import java.io.Serializable;
import java.util.SortedSet;

public record TradeProposal(
        String proposalId,
        String requesterUsername,
        String recipientUsername,
        SortedSet<Integer> requesterOffer,
        SortedSet<Integer> recipientOfferCandidates,
        boolean recipientSelectionRequired,
        int expectedRecipientSelectionSize) implements Serializable {

    public TradeProposal {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("Trade proposals require an id.");
        }
        if (requesterUsername == null || requesterUsername.isBlank()) {
            throw new IllegalArgumentException("Requester username is required.");
        }
        if (recipientUsername == null || recipientUsername.isBlank()) {
            throw new IllegalArgumentException("Recipient username is required.");
        }
        requesterOffer = StickerSets.normalize(requesterOffer);
        recipientOfferCandidates = StickerSets.normalize(recipientOfferCandidates);
        if (requesterOffer.isEmpty() || recipientOfferCandidates.isEmpty()) {
            throw new IllegalArgumentException("Trade proposals must include stickers from both users.");
        }
        if (recipientSelectionRequired) {
            if (expectedRecipientSelectionSize <= 0) {
                throw new IllegalArgumentException("Recipient selection size must be positive.");
            }
            if (requesterOffer.size() != expectedRecipientSelectionSize) {
                throw new IllegalArgumentException("The requester offer must match the recipient selection size.");
            }
            if (recipientOfferCandidates.size() <= expectedRecipientSelectionSize) {
                throw new IllegalArgumentException("Recipient selection only makes sense when there are extra choices.");
            }
        } else {
            expectedRecipientSelectionSize = recipientOfferCandidates.size();
            if (requesterOffer.size() != recipientOfferCandidates.size()) {
                throw new IllegalArgumentException("Fixed proposals must trade the same number of stickers on both sides.");
            }
        }
    }

    public SortedSet<Integer> fixedRecipientOffer() {
        if (recipientSelectionRequired) {
            throw new IllegalStateException("The recipient still needs to choose stickers for this trade.");
        }
        return recipientOfferCandidates;
    }
}
