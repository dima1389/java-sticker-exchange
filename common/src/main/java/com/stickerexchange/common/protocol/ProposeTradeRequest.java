package com.stickerexchange.common.protocol;

// Helper that tidies sticker-number sets.
import com.stickerexchange.common.util.StickerSets;
// Sorted, duplicate-free collection type.
import java.util.SortedSet;

/**
 * ProposeTradeRequest — sent by the client when the user clicks "Propose trade". It describes the deal
 * the user wants to offer to someone else. The server validates it and, if all is well, turns it into
 * a TradeProposal that gets delivered to the recipient.
 *
 * <p>FIELDS:
 * <ul>
 *   <li>{@code targetUsername} — who to trade with.</li>
 *   <li>{@code requesterOffer} — the stickers the user is offering to give.</li>
 *   <li>{@code recipientOfferCandidates} — the stickers the user wants in return (possibly a wider
 *       pool the recipient will narrow down).</li>
 *   <li>{@code recipientSelectionRequired} — true if the recipient must pick from those candidates.</li>
 *   <li>{@code expectedRecipientSelectionSize} — how many stickers the recipient should send back.</li>
 * </ul>
 */
public record ProposeTradeRequest(
        String targetUsername,
        SortedSet<Integer> requesterOffer,
        SortedSet<Integer> recipientOfferCandidates,
        boolean recipientSelectionRequired,
        int expectedRecipientSelectionSize) implements Message {

    // Compact constructor: clean and sanity-check the request before it leaves the client.
    public ProposeTradeRequest {
        // You must say WHO you are trading with; reject a missing or blank target name.
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new IllegalArgumentException("Target username is required.");
        }
        // Tidy both sticker sets into safe, sorted, unmodifiable copies.
        requesterOffer = StickerSets.normalize(requesterOffer);
        recipientOfferCandidates = StickerSets.normalize(recipientOfferCandidates);
        // A trade needs stickers flowing both ways; neither set may be empty.
        if (requesterOffer.isEmpty() || recipientOfferCandidates.isEmpty()) {
            throw new IllegalArgumentException("Trade requests need stickers on both sides.");
        }
        // The number of stickers expected back must be a positive count (at least 1). "<=" means "less than or equal".
        if (expectedRecipientSelectionSize <= 0) {
            throw new IllegalArgumentException("Expected selection size must be positive.");
        }
    }
}
