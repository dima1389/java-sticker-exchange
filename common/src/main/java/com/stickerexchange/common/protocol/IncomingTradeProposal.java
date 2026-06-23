package com.stickerexchange.common.protocol;

// Wraps a TradeProposal (the actual deal), so import it.
import com.stickerexchange.common.model.TradeProposal;
// Toolbox used for the null-check below.
import java.util.Objects;

/**
 * IncomingTradeProposal — the server PUSHES this to a user when someone else proposes a trade with
 * them. It is a thin envelope around a TradeProposal.
 *
 * <p>WHY WRAP IT: TradeProposal lives in the "model" package and is not itself a Message. Wrapping it
 * in this record turns it into something the network layer can send and the receiver's switch can
 * recognise as "a trade is being offered to me".
 */
public record IncomingTradeProposal(TradeProposal proposal) implements Message {
    // Compact constructor: an empty envelope would be meaningless, so the proposal must be present.
    public IncomingTradeProposal {
        Objects.requireNonNull(proposal, "Trade proposal is required.");
    }
}
