package com.stickerexchange.common.protocol;

/**
 * FindMatchesRequest — sent by the client to ask "who can I trade with right now?". It carries NO
 * data: just sending this type IS the whole request. The server figures out the answer from the
 * albums it already stores.
 *
 * <p>KEY CONCEPT — empty record: the parentheses after the name are empty because there are no
 * fields. The message's meaning comes purely from its TYPE, which the receiver detects via a switch.
 */
public record FindMatchesRequest() implements Message {
}
