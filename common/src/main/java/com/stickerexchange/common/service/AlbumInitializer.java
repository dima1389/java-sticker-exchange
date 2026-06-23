// Business-logic ("service") package.
package com.stickerexchange.common.service;

// We build AlbumState objects, so we import that data type.
import com.stickerexchange.common.model.AlbumState;
// "ArrayList" is a resizable list backed by an array — a good general-purpose list.
import java.util.ArrayList;
// "Collections" toolbox; here we use it to shuffle a list randomly.
import java.util.Collections;
// The List interface (the general "ordered collection" type).
import java.util.List;
// "Random" produces random numbers; it is how we make each new album different.
import java.util.Random;
// TreeSet: sorted, duplicate-free set used for the final sticker groups.
import java.util.TreeSet;
// "Collectors" provides recipes for gathering a stream's results into a collection.
import java.util.stream.Collectors;
// "IntStream" is a stream specialised for primitive int values — handy for number ranges.
import java.util.stream.IntStream;

/**
 * AlbumInitializer — creates a fresh, RANDOM starter album for each new user who connects.
 *
 * <p>WHAT IT DOES: it imagines all stickers 1..99, shuffles them, then hands the user a random
 * handful of "duplicates" and a separate random handful of "missing" stickers. Because they are
 * drawn from one shuffled pile, the two groups never overlap — satisfying AlbumState's rules.
 *
 * <p>KEY CONCEPT — dependency injection for testability: the source of randomness ({@code Random}) is
 * passed into the constructor rather than hard-coded. Normal code uses the no-argument constructor
 * (truly random), but a test can pass a Random with a fixed "seed" to get predictable results. This
 * makes the class easy to test.
 *
 * <p>HOW IT INTERACTS: the server's ExchangeCoordinator owns one AlbumInitializer and calls
 * {@code createRandomAlbum()} whenever a new user registers.
 */
// Utility-style service. "final" prevents subclassing.
public final class AlbumInitializer {
    // The fewest stickers a new user starts with in each group. "private static final" = a constant internal
    // to this class.
    private static final int MIN_INITIAL_STICKERS = 6;
    // The most stickers a new user can start with in each group.
    private static final int MAX_INITIAL_STICKERS = 15;

    // The random-number generator this instance uses. "final" means once set in the constructor it never
    // changes. It is an instance field (no "static") because each initializer carries its own generator.
    private final Random random;

    // The no-argument ("default") constructor most callers use. "this(new Random())" forwards to the other
    // constructor below, supplying a brand-new, truly random generator. Calling one constructor from another
    // like this avoids duplicating setup code.
    public AlbumInitializer() {
        this(new Random());
    }

    // The main constructor. It receives a Random from outside (dependency injection) and stores it.
    public AlbumInitializer(Random random) {
        // "this.random" means the field of THIS object; "= random" copies in the value of the parameter. The
        // "this." prefix distinguishes the field from the parameter, which share the name "random".
        this.random = random;
    }

    // Builds and returns one new random album.
    public AlbumState createRandomAlbum() {
        // Create a list of every sticker number from MIN_STICKER (1) to MAX_STICKER (99), inclusive.
        //   - "IntStream.rangeClosed(1, 99)" produces the numbers 1,2,...,99 ("closed" = include the end).
        //   - ".boxed()" converts each primitive int into an Integer object so it can live in a list.
        //   - ".collect(Collectors.toCollection(ArrayList::new))" gathers them into a new ArrayList.
        List<Integer> allStickers = IntStream.rangeClosed(AlbumState.MIN_STICKER, AlbumState.MAX_STICKER)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        // Randomly reorder the whole list using our Random. After this, the first numbers are an unpredictable pick.
        Collections.shuffle(allStickers, random);

        // Decide how many duplicates this user gets (a random count between the min and max).
        int duplicateCount = randomCount();
        // Decide how many missing stickers this user gets (independently random).
        int missingCount = randomCount();
        // Take the first "duplicateCount" numbers from the shuffled list as the duplicates. "subList(from, to)"
        // returns the slice from index "from" up to (but not including) "to"; wrapping it in a TreeSet sorts it.
        TreeSet<Integer> duplicates = new TreeSet<>(allStickers.subList(0, duplicateCount));
        // Take the NEXT slice (right after the duplicates) as the missing stickers, so the two groups never overlap.
        TreeSet<Integer> missing = new TreeSet<>(allStickers.subList(duplicateCount, duplicateCount + missingCount));
        // Build the album from the two non-overlapping groups and return it.
        return new AlbumState(duplicates, missing);
    }

    // Private helper returning a random whole number between the min and max starter counts (inclusive).
    private int randomCount() {
        // "nextInt(origin, bound)" returns a random int from "origin" up to (but not including) "bound", so we
        // add 1 to the max to make the maximum reachable.
        return random.nextInt(MIN_INITIAL_STICKERS, MAX_INITIAL_STICKERS + 1);
    }
}
