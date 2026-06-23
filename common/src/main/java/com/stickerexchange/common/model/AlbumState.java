// A "package" is like a folder or family name for related classes. It groups code together
// and gives every class a unique full name. This line MUST be the first real line of the file
// and it tells Java that the class below belongs to the "com.stickerexchange.common.model" family.
package com.stickerexchange.common.model;

// An "import" lets us use a class that lives in a DIFFERENT package without typing its full name
// every time. Think of it as adding a contact to your phone so you can call them by first name.
// Here we borrow our own helper class "StickerSets" (it cleans up sets of sticker numbers).
import com.stickerexchange.common.util.StickerSets;
// "Serializable" is a built-in Java marker. It means "objects of this type are allowed to be
// turned into a stream of bytes" so they can be sent over the network between client and server.
import java.io.Serializable;
// "Collection" is a general family name for any group of items (a list, a set, etc.).
import java.util.Collection;
// "SortedSet" is a collection that (1) holds no duplicates and (2) keeps its items in order.
import java.util.SortedSet;
// "TreeSet" is one concrete kind of SortedSet. When we need to actually CREATE a sorted set,
// we make a TreeSet.
import java.util.TreeSet;

/**
 * AlbumState — the central data object of this whole project.
 *
 * <p>WHAT IT REPRESENTS: a single user's sticker album situation, described by two groups of
 * numbers:
 * <ul>
 *   <li>{@code duplicates} — sticker numbers the user owns MORE than once (spares to give away).</li>
 *   <li>{@code missing}    — sticker numbers the user does NOT own yet (gaps to fill).</li>
 * </ul>
 *
 * <p>KEY CONCEPT — "record": Notice the word {@code record} instead of {@code class}. A record is
 * a short way to create an IMMUTABLE data holder. "Immutable" means once an AlbumState is built,
 * its contents can never be changed. The record automatically gives us a constructor, getter-style
 * methods named after each field ({@code duplicates()} and {@code missing()}), plus {@code equals},
 * {@code hashCode}, and {@code toString}. We get all that for free instead of writing it by hand.
 *
 * <p>KEY CONCEPT — "validation": A sticker number must be between 1 and 99, and the same number can
 * never be in both groups at once. This class checks (validates) those rules the moment an album is
 * created, so the rest of the program can always trust that an AlbumState is well-formed.
 *
 * <p>HOW IT INTERACTS WITH OTHER FILES: AlbumInitializer creates random AlbumStates for new users,
 * ExchangeCalculator compares two AlbumStates to find trades, the server stores one per user, and
 * the client UI (StickerExchangeFrame) shows it on screen. Because it implements Serializable, it
 * also travels across the network inside protocol messages.
 */
// "public" = any other class is allowed to use this. "record" = an immutable data holder (see above).
// The two items inside the parentheses are the record's "components" (its fields): the duplicates set
// and the missing set. "implements Serializable" promises this type can be converted to bytes.
public record AlbumState(SortedSet<Integer> duplicates, SortedSet<Integer> missing) implements Serializable {
    // "public static final int" creates a constant whole number shared by ALL albums.
    //   - "static" means it belongs to the class itself, not to one particular album object.
    //   - "final" means its value can never be reassigned (it is a true constant).
    //   - "int" is the type for whole numbers. The lowest valid sticker number is 1.
    public static final int MIN_STICKER = 1;
    // The highest valid sticker number is 99. Using a named constant (instead of writing 99 all over
    // the code) makes the rule easy to find and change in one place.
    public static final int MAX_STICKER = 99;

    // This is a "compact constructor" — special syntax only records have. It runs automatically every
    // time someone creates an AlbumState, BEFORE the values are stored. We use it to clean and check
    // the incoming data. Notice there are no parentheses or parameter list: the record supplies them.
    public AlbumState {
        // Replace the incoming "duplicates" with a tidied, unmodifiable, sorted copy. "StickerSets.normalize"
        // is our helper. The "=" operator assigns the cleaned value back to the field of the same name.
        duplicates = StickerSets.normalize(duplicates);
        // Do the same tidy-up for the "missing" set so both fields are stored in a consistent shape.
        missing = StickerSets.normalize(missing);
        // Run our rule that every number must be between 1 and 99 for the duplicates group.
        validateRange(duplicates);
        // Run the same range rule for the missing group.
        validateRange(missing);
        // Run the rule that no number may appear in BOTH groups at the same time.
        validateNoOverlap(duplicates, missing);
    }

    // A "static factory method": a method you call on the class (AlbumState.empty()) to get an object,
    // instead of using "new". "static" = belongs to the class. It returns a brand-new album with two
    // empty sets, handy as a safe starting point before a user has any stickers.
    public static AlbumState empty() {
        // "new TreeSet<>()" builds an empty sorted set. The "<>" (diamond) lets Java figure out it holds
        // Integers from context. We pass two empty sets into the normal AlbumState constructor.
        return new AlbumState(new TreeSet<>(), new TreeSet<>());
    }

    // Another factory method. It accepts any kind of Collection (list, set, ...) and converts each into
    // a TreeSet first, so callers don't have to build sorted sets themselves. This is a convenience door.
    public static AlbumState of(Collection<Integer> duplicates, Collection<Integer> missing) {
        // Wrap each incoming collection in a new TreeSet (sorting + removing duplicates) and build the album.
        return new AlbumState(new TreeSet<>(duplicates), new TreeSet<>(missing));
    }

    // An "instance method" (no "static"): you call it on a specific album object. It answers a yes/no
    // question, so its return type is "boolean" (a value that is either true or false).
    public boolean duplicatesContainAll(Collection<Integer> stickers) {
        // "containsAll" returns true only if EVERY number in "stickers" is present in our duplicates set.
        // We "return" that true/false answer straight to whoever asked.
        return duplicates.containsAll(stickers);
    }

    // Same idea as above, but checks the "missing" set instead. Used to confirm a user really lacks the
    // stickers a trade claims they are missing.
    public boolean missingContainAll(Collection<Integer> stickers) {
        // Ask the missing set whether it contains all the requested numbers and return the answer.
        return missing.containsAll(stickers);
    }

    // Because a record is immutable, we never edit an album in place. Instead we build a NEW album that
    // is a copy with some duplicates removed. This method returns that new album.
    public AlbumState withoutDuplicates(Collection<Integer> removedDuplicates) {
        // Make a fresh, modifiable copy of the current duplicates so we can change the copy safely.
        TreeSet<Integer> updatedDuplicates = new TreeSet<>(duplicates);
        // "removeAll" deletes every number listed in "removedDuplicates" from our copy.
        updatedDuplicates.removeAll(removedDuplicates);
        // Build and return a brand-new album using the trimmed duplicates and an untouched copy of missing.
        return new AlbumState(updatedDuplicates, new TreeSet<>(missing));
    }

    // The mirror image of the method above: returns a new album with some "missing" numbers removed
    // (used after a user receives stickers they were missing).
    public AlbumState withoutMissing(Collection<Integer> removedMissing) {
        // Copy the missing set so the original stays unchanged.
        TreeSet<Integer> updatedMissing = new TreeSet<>(missing);
        // Remove the numbers that are no longer missing.
        updatedMissing.removeAll(removedMissing);
        // Return a new album with an untouched copy of duplicates and the trimmed missing set.
        return new AlbumState(new TreeSet<>(duplicates), updatedMissing);
    }

    // A "private" helper method: only code INSIDE this class may call it. "static" because it works on
    // values passed in, not on a particular album. "void" means it returns nothing — it only checks and
    // throws an error if a rule is broken.
    private static void validateRange(Collection<Integer> values) {
        // A "for-each" loop: it visits every Integer in "values" one at a time, calling each one "value".
        for (Integer value : values) {
            // "if" runs the block only when its condition is true. The "||" means OR. We reject the value if
            // it is null (missing), OR smaller than the minimum, OR bigger than the maximum.
            if (value == null || value < MIN_STICKER || value > MAX_STICKER) {
                // "throw" stops the method immediately and reports an error. An IllegalArgumentException signals
                // "you gave me a value I'm not allowed to accept", with a human-readable message.
                throw new IllegalArgumentException("Sticker numbers must stay in the range 1-99.");
            }
        }
    }

    // Another private rule-checker: makes sure the same sticker number is not listed as both a duplicate
    // and missing, which would be contradictory.
    private static void validateNoOverlap(Collection<Integer> duplicates, Collection<Integer> missing) {
        // Look at every number in the duplicates group.
        for (Integer value : duplicates) {
            // If that number also appears in the missing group, the album contradicts itself, so reject it.
            if (missing.contains(value)) {
                throw new IllegalArgumentException("A sticker cannot be both a duplicate and missing.");
            }
        }
    }
}
