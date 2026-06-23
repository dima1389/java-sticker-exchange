// This class belongs to the same "model" family as AlbumState — these are the core data objects.
package com.stickerexchange.common.model;

// Borrow our helper that tidies sets of sticker numbers (see StickerSets.java).
import com.stickerexchange.common.util.StickerSets;
// Marker that allows objects of this type to be sent across the network as bytes.
import java.io.Serializable;
// A collection with no duplicates whose items stay in sorted order.
import java.util.SortedSet;

/**
 * TradeMatch — describes ONE possible trade between the current user and one other user.
 *
 * <p>WHAT IT REPRESENTS: when two users compare albums, a "match" exists if each person owns spare
 * stickers the other is missing. This record captures that opportunity with three pieces of data:
 * <ul>
 *   <li>{@code otherUsername}        — who you could trade with.</li>
 *   <li>{@code offerFromCurrentUser} — sticker numbers YOU could hand over (your spares they need).</li>
 *   <li>{@code offerFromOtherUser}   — sticker numbers THEY could hand over (their spares you need).</li>
 * </ul>
 *
 * <p>KEY CONCEPT — uneven trades: the two offers might be different sizes. If one side has more
 * stickers available than the other, the side with MORE must pick exactly which ones to trade so
 * both sides exchange the same count. The helper methods below report who must choose.
 *
 * <p>HOW IT INTERACTS: ExchangeCalculator creates TradeMatch objects, MatchesResponse carries a list
 * of them to the client, and the UI shows them so the user can propose a trade.
 */
// A record (immutable data holder) with three components, serializable so it can travel over the network.
public record TradeMatch(
        String otherUsername,
        SortedSet<Integer> offerFromCurrentUser,
        SortedSet<Integer> offerFromOtherUser) implements Serializable {

    // Compact constructor: runs at creation time to clean and validate the data.
    public TradeMatch {
        // Guard clause: a match makes no sense without naming the other user. "isBlank()" is true when a
        // String is empty or only spaces. We reject a null (missing) or blank username.
        if (otherUsername == null || otherUsername.isBlank()) {
            throw new IllegalArgumentException("Trade matches require a username.");
        }
        // Tidy both offers into unmodifiable, sorted copies so they are safe to share and compare.
        offerFromCurrentUser = StickerSets.normalize(offerFromCurrentUser);
        offerFromOtherUser = StickerSets.normalize(offerFromOtherUser);
        // A real trade needs stickers flowing BOTH ways. If either side is empty, there is nothing to swap.
        if (offerFromCurrentUser.isEmpty() || offerFromOtherUser.isEmpty()) {
            throw new IllegalArgumentException("Trade matches need stickers on both sides.");
        }
    }

    // Returns true when YOUR pile of offerable stickers is bigger than theirs, meaning you must pick which
    // ones to give. ".size()" returns how many items are in a set; ">" is the "greater than" comparison.
    public boolean currentUserMustSelect() {
        return offerFromCurrentUser.size() > offerFromOtherUser.size();
    }

    // The mirror question: true when the OTHER user has the bigger pile and therefore must choose.
    public boolean otherUserMustSelect() {
        return offerFromOtherUser.size() > offerFromCurrentUser.size();
    }

    // The actual number of stickers that will change hands on each side: the SMALLER of the two pile sizes,
    // because a fair trade can only be as big as the shorter side allows. "Math.min" returns the lesser value.
    public int tradeSize() {
        return Math.min(offerFromCurrentUser.size(), offerFromOtherUser.size());
    }

    // "@Override" tells Java we are replacing a method that already exists in a parent type (here, the
    // default toString from Object/record). toString() decides how this object looks when shown as text —
    // for example inside the dropdown list in the UI. "+" joins strings together.
    @Override
    public String toString() {
        return otherUsername + " (" + tradeSize() + " stickers)";
    }
}
