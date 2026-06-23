// A "util" (utility) package holds small, general-purpose helper code reused across the project.
package com.stickerexchange.common.util;

// "Collections" is a built-in toolbox class full of static helper methods for working with collections.
import java.util.Collections;
// "Objects" is another toolbox class; we use it for a convenient null-check.
import java.util.Objects;
// A sorted, duplicate-free collection type (the kind of value this helper accepts and returns).
import java.util.SortedSet;
// The concrete sorted-set implementation we build copies with.
import java.util.TreeSet;

/**
 * StickerSets — a tiny utility class with one job: produce a clean, safe version of a set of sticker
 * numbers. Several different classes (AlbumState, TradeMatch, TradeProposal, and protocol messages)
 * all need the same "tidy up this set" behaviour, so we write it ONCE here and everyone reuses it.
 * This is the DRY principle: Don't Repeat Yourself.
 *
 * <p>KEY CONCEPT — utility class: it holds only {@code static} methods (called on the class, not on an
 * object) and is never meant to be instantiated. To enforce that, its constructor is private.
 */
// "final" on a class means no other class is allowed to extend (inherit from) it — appropriate for a
// simple helper that should not be customised.
public final class StickerSets {
    // A private, empty constructor. Because it is private, no code outside this class can write
    // "new StickerSets()". This is a deliberate trick to say "this class is a bag of static helpers,
    // do not create objects from it".
    private StickerSets() {
    }

    // The one helper method. "static" = call it as StickerSets.normalize(...). It takes a SortedSet of
    // Integers and returns a cleaned-up SortedSet of Integers.
    public static SortedSet<Integer> normalize(SortedSet<Integer> values) {
        // "requireNonNull" throws a clear error if "values" is null, stopping bugs early instead of letting a
        // confusing NullPointerException surface later. The second argument is the message shown if it fails.
        Objects.requireNonNull(values, "Sticker sets must not be null.");
        // Build the result in two wrapped steps, read inside-out:
        //   1. "new TreeSet<>(values)" copies the numbers into a fresh sorted set (sorted, no duplicates).
        //   2. "Collections.unmodifiableSortedSet(...)" wraps that copy so nobody can add or remove items later.
        // Returning an unmodifiable copy protects our data objects from being changed behind their backs.
        return Collections.unmodifiableSortedSet(new TreeSet<>(values));
    }
}
