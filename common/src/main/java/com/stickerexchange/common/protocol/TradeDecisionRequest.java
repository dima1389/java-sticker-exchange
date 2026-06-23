package com.stickerexchange.common.protocol;

// Helper that tidies sticker-number sets.
import com.stickerexchange.common.util.StickerSets;
// Sorted, duplicate-free collection type.
import java.util.SortedSet;

/**
 * TradeDecisionRequest — sent by the RECIPIENT of a trade to answer it: accept or decline. If they
 * accept and had to choose which stickers to give, their chosen stickers ride along here too.
 *
 * <p>FIELDS:
 * <ul>
 *   <li>{@code proposalId} — which proposal this answers (matches the id the server assigned).</li>
 *   <li>{@code accept} — true to accept the trade, false to decline.</li>
 *   <li>{@code selectedRecipientOffer} — the stickers the recipient chose to give (empty if none
 *       needed choosing or if declining).</li>
 * </ul>
 */
public record TradeDecisionRequest(String proposalId, boolean accept, SortedSet<Integer> selectedRecipientOffer) implements Message {
    // Compact constructor: validate and tidy the answer.
    public TradeDecisionRequest {
        // We must know WHICH proposal is being answered, so an id is mandatory.
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("Proposal id is required.");
        }
        // Tidy the chosen stickers into a safe, sorted, unmodifiable set (it may legitimately be empty).
        selectedRecipientOffer = StickerSets.normalize(selectedRecipientOffer);
    }
}
