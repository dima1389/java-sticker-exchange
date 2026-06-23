// Part of the client/server message "language".
package com.stickerexchange.common.protocol;

/**
 * RegisterRequest — the FIRST message a client sends. It asks the server "please sign me in under
 * this username". It carries a single piece of data: the desired username.
 *
 * <p>HOW IT INTERACTS: the client's ClientController sends this right after connecting; the server's
 * ExchangeCoordinator replies with a RegisterResponse (success or failure).
 */
// A record (immutable data holder) with one field, "username". "implements Message" marks it as
// something that can be sent over the network.
public record RegisterRequest(String username) implements Message {
    // Compact constructor: validates the username the moment the request is built.
    public RegisterRequest {
        // Reject a username that is missing (null) or blank (empty / only spaces). "||" means OR.
        if (username == null || username.isBlank()) {
            // Stop and report the problem rather than sending an invalid request to the server.
            throw new IllegalArgumentException("Username is required.");
        }
    }
}
