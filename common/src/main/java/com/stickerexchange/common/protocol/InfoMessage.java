package com.stickerexchange.common.protocol;

// May carry the user's current album so the client can refresh its view; import AlbumState.
import com.stickerexchange.common.model.AlbumState;

/**
 * InfoMessage — a general-purpose notice from the server to the client. It is used for all sorts of
 * one-off updates: "trade request sent", "that user is offline", "register first", and so on.
 *
 * <p>FIELDS: {@code success} (was the related action OK?), {@code message} (text to show the user),
 * and {@code albumState} (optional refreshed album; may be null when not relevant).
 */
public record InfoMessage(boolean success, String message, AlbumState albumState) implements Message {
}
