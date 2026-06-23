package com.stickerexchange.common.protocol;

// The reply includes the user's starting album, so we import the AlbumState data type.
import com.stickerexchange.common.model.AlbumState;

/**
 * RegisterResponse — the server's answer to a RegisterRequest. It reports whether sign-in worked and,
 * if it did, hands back the random starter album the server created for this user.
 *
 * <p>FIELDS: {@code success} (did it work?), {@code message} (human-readable explanation to show the
 * user), and {@code albumState} (the starter album, or null when sign-in failed).
 */
// A simple immutable data holder with three fields and no extra validation. "boolean" holds true/false.
public record RegisterResponse(boolean success, String message, AlbumState albumState) implements Message {
}
