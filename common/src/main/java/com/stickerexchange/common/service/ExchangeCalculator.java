// The "service" package holds classes that perform calculations / business logic (the "verbs" of the
// app), as opposed to the "model" package which holds data (the "nouns").
package com.stickerexchange.common.service;

// We work with AlbumState (a user's stickers) and produce TradeMatch results, so we import both.
import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
// "Comparator" lets us describe how to sort objects.
import java.util.Comparator;
// "List" is an ordered collection that allows duplicates and keeps insertion order.
import java.util.List;
// "Map" is a lookup table of key -> value pairs (here: username -> their album).
import java.util.Map;
// "Optional" is a box that either contains a value or is empty — a safe alternative to returning null.
import java.util.Optional;
// Sorted, duplicate-free collection and its concrete TreeSet implementation.
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * ExchangeCalculator — the "brain" that figures out which trades are possible.
 *
 * <p>WHAT IT DOES: given one user's album and a map of everybody else's albums, it works out every
 * worthwhile trade. A trade between two people is worthwhile only when each person owns spare
 * stickers (duplicates) that the OTHER person is missing.
 *
 * <p>KEY CONCEPT — pure functions: every method here is {@code static} and only reads its inputs to
 * compute an output. It changes no shared state, which makes the logic easy to reason about and test.
 *
 * <p>KEY CONCEPT — set intersection: the core trick is "intersection" — the numbers two sets have in
 * common. Your offerable stickers = (your duplicates) ∩ (their missing).
 *
 * <p>HOW IT INTERACTS: the server's ExchangeCoordinator calls these methods to answer "find matches"
 * and to validate proposed trades.
 */
// Utility class: only static methods, never instantiated, so it is final with a private constructor.
public final class ExchangeCalculator {
    // Private constructor blocks anyone from creating an ExchangeCalculator object.
    private ExchangeCalculator() {
    }

    // Works out whether ONE specific pair of users can trade. Returns an Optional: filled with a
    // TradeMatch if a trade exists, or empty if it does not. This avoids returning null.
    public static Optional<TradeMatch> calculateMatch(String otherUsername, AlbumState currentAlbum, AlbumState otherAlbum) {
        // What I can give = stickers I have spare (my duplicates) that the other person still needs (their missing).
        SortedSet<Integer> offerFromCurrentUser = intersection(currentAlbum.duplicates(), otherAlbum.missing());
        // What they can give = their spares that I am missing. Symmetric to the line above.
        SortedSet<Integer> offerFromOtherUser = intersection(otherAlbum.duplicates(), currentAlbum.missing());
        // If either side has nothing useful to offer, no trade is possible, so return an empty Optional.
        if (offerFromCurrentUser.isEmpty() || offerFromOtherUser.isEmpty()) {
            return Optional.empty();
        }
        // Both sides can offer something, so wrap a new TradeMatch in an Optional and return it.
        return Optional.of(new TradeMatch(otherUsername, offerFromCurrentUser, offerFromOtherUser));
    }

    // Works out ALL matches between the current user and everyone else, returning them as a sorted List.
    // "Map<String, AlbumState>" maps each other user's name to their album.
    public static List<TradeMatch> calculateMatches(AlbumState currentAlbum, Map<String, AlbumState> otherAlbums) {
        // This is a "stream pipeline": a fluent, step-by-step way to process a collection. Read it top to bottom.
        return otherAlbums.entrySet().stream()
                // ".stream()" turns the set of (name, album) entries into a flowing sequence we can transform.
                // ".map(...)" converts each entry into an Optional<TradeMatch> by calling calculateMatch.
                // "entry.getKey()" is the username; "entry.getValue()" is that user's album. The "->" introduces a
                // lambda: a short anonymous function whose input (entry) is on the left and result on the right.
                .map(entry -> calculateMatch(entry.getKey(), currentAlbum, entry.getValue()))
                // ".flatMap(Optional::stream)" drops the empty Optionals and unwraps the present ones, leaving a
                // clean stream of actual TradeMatch values. "Optional::stream" is a method reference (shorthand
                // for the lambda "o -> o.stream()").
                .flatMap(Optional::stream)
                // ".sorted(...)" orders the matches by the other user's name, ignoring upper/lower case so the list
                // reads alphabetically. "TradeMatch::otherUsername" is a method reference used to pick the sort key.
                .sorted(Comparator.comparing(TradeMatch::otherUsername, String.CASE_INSENSITIVE_ORDER))
                // ".toList()" collects the finished stream back into an ordinary List to return.
                .toList();
    }

    // Private helper computing the intersection (shared members) of two sets WITHOUT changing either input.
    private static SortedSet<Integer> intersection(SortedSet<Integer> left, SortedSet<Integer> right) {
        // Copy "left" into a new mutable TreeSet so we can safely modify the copy.
        TreeSet<Integer> result = new TreeSet<>(left);
        // "retainAll" keeps only the items that ALSO appear in "right", deleting the rest — exactly an intersection.
        result.retainAll(right);
        // Return the numbers the two sets had in common.
        return result;
    }
}
