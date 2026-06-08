package com.stickerexchange.common.protocol;

import com.stickerexchange.common.model.AlbumState;
import java.util.Objects;

public record AlbumSyncRequest(AlbumState albumState) implements Message {
    public AlbumSyncRequest {
        Objects.requireNonNull(albumState, "Album state is required.");
    }
}
