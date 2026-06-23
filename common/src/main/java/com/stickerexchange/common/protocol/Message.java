// The "protocol" package defines the "language" the client and server use to talk to each other.
// Every kind of network message lives here.
package com.stickerexchange.common.protocol;

// "Serializable" is the built-in marker that allows an object to be converted to bytes for sending.
import java.io.Serializable;

/**
 * Message — the common parent type for EVERY message exchanged between client and server.
 *
 * <p>KEY CONCEPT — interface: an {@code interface} is a contract / category, not a concrete object.
 * It says "anything labelled a Message can be sent across the network". Many different records
 * (RegisterRequest, MatchesResponse, etc.) declare {@code implements Message}, which lets us treat
 * them all uniformly — for example, a single method can accept "any Message".
 *
 * <p>KEY CONCEPT — marker interface: this interface adds no methods of its own. Its only job is to
 * group types together and, by extending {@code Serializable}, guarantee they can all be turned into
 * bytes. Such an interface with no methods is called a "marker".
 *
 * <p>KEY CONCEPT — sealed-like routing: the server and client use a {@code switch} over the concrete
 * Message type to decide how to react to each one (you will see this in ServerMain and
 * ClientController).
 */
// "public interface" declares the contract. "extends Serializable" means every Message is also
// automatically a Serializable, inheriting that capability.
public interface Message extends Serializable {
}
