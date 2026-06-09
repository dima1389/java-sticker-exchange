package com.stickerexchange.common.protocol;

import com.stickerexchange.common.model.TradeMatch;
import java.util.List;

public record MatchesResponse(List<TradeMatch> matches) implements Message {
    public MatchesResponse {
        matches = List.copyOf(matches);
    }
}
