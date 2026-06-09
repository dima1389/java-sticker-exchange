package com.stickerexchange.common.protocol;

import com.stickerexchange.common.util.StickerSets;
import java.util.SortedSet;

public record TradeDecisionRequest(String proposalId, boolean accept, SortedSet<Integer> selectedRecipientOffer) implements Message {
    public TradeDecisionRequest {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("Proposal id is required.");
        }
        selectedRecipientOffer = StickerSets.normalize(selectedRecipientOffer);
    }
}
