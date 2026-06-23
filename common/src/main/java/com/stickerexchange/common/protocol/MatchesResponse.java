package com.stickerexchange.common.protocol;

// Carries many TradeMatch objects, so import that type and the List collection type.
import com.stickerexchange.common.model.TradeMatch;
import java.util.List;

/**
 * MatchesResponse — the server's answer to a FindMatchesRequest. It carries a list of every trade the
 * user could currently make, which the client shows in its matches dropdown.
 *
 * <p>KEY CONCEPT — defensive copy: the compact constructor replaces the incoming list with an
 * unmodifiable copy so the data inside this message can never be altered after it is built.
 */
public record MatchesResponse(List<TradeMatch> matches) implements Message {
    // Compact constructor.
    public MatchesResponse {
        // "List.copyOf" makes a new, read-only copy of the list. This protects the message from later changes
        // to the original list and guarantees the data stays consistent.
        matches = List.copyOf(matches);
    }
}
