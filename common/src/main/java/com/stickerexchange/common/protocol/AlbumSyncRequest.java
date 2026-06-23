package com.stickerexchange.common.protocol;

// Carries an AlbumState, so we import it.
import com.stickerexchange.common.model.AlbumState;
// "Objects" toolbox, used here for a tidy null-check.
import java.util.Objects;

/**
 * AlbumSyncRequest — sent by the client to SAVE the user's edited album on the server. "Sync" means
 * "make the server's copy match what I have on screen".
 *
 * <p>HOW IT INTERACTS: triggered when the user clicks "Save album"; the server replies with an
 * AlbumSyncResponse.
 */
public record AlbumSyncRequest(AlbumState albumState) implements Message {
    // Compact constructor: there is nothing to save if no album was supplied.
    public AlbumSyncRequest {
        // "requireNonNull" throws immediately with a clear message if albumState is null.
        Objects.requireNonNull(albumState, "Album state is required.");
    }
}
