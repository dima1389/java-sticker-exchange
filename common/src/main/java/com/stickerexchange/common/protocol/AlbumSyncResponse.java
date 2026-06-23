package com.stickerexchange.common.protocol;

// The confirmed, saved album is returned, so import AlbumState.
import com.stickerexchange.common.model.AlbumState;

/**
 * AlbumSyncResponse — the server's reply to an AlbumSyncRequest. It says whether the save succeeded,
 * gives a message to display, and echoes back the album the server now holds so the client stays in
 * step.
 */
public record AlbumSyncResponse(boolean success, String message, AlbumState albumState) implements Message {
}
