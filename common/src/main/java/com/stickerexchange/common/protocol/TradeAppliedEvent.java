package com.stickerexchange.common.protocol;

import com.stickerexchange.common.model.AlbumState;

public record TradeAppliedEvent(String counterpartUsername, String message, AlbumState albumState) implements Message {
}
