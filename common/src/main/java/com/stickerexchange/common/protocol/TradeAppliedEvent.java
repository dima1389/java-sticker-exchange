package com.stickerexchange.common.protocol;

// Sends each user their UPDATED album after a successful swap; import AlbumState.
import com.stickerexchange.common.model.AlbumState;

/**
 * TradeAppliedEvent — the server sends this to BOTH users once a trade has actually been carried out
 * and their albums have changed. It is the "the swap is done" notification.
 *
 * <p>FIELDS: {@code counterpartUsername} (who you traded with), {@code message} (text to show), and
 * {@code albumState} (your new album after the swap, so the UI can redraw it).
 */
public record TradeAppliedEvent(String counterpartUsername, String message, AlbumState albumState) implements Message {
}
