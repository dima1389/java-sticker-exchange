package com.stickerexchange.common.protocol;

import com.stickerexchange.common.model.AlbumState;

public record InfoMessage(boolean success, String message, AlbumState albumState) implements Message {
}
