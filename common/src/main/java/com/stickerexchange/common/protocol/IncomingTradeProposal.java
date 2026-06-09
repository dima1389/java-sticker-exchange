package com.stickerexchange.common.protocol;

import com.stickerexchange.common.model.TradeProposal;
import java.util.Objects;

public record IncomingTradeProposal(TradeProposal proposal) implements Message {
    public IncomingTradeProposal {
        Objects.requireNonNull(proposal, "Trade proposal is required.");
    }
}
