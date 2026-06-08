package com.stickerexchange.common.protocol;

import com.stickerexchange.common.model.AlbumState;

public record RegisterResponse(boolean success, String message, AlbumState albumState) implements Message {
}
