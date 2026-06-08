package com.stickerexchange.common.protocol;

public record RegisterRequest(String username) implements Message {
    public RegisterRequest {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
    }
}
