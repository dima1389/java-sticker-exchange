// Part of the core "model" data objects.
package com.stickerexchange.common.model;

// Helper for tidying sticker-number sets.
import com.stickerexchange.common.util.StickerSets;
// Lets these objects be turned into bytes for network travel.
import java.io.Serializable;
// Sorted, duplicate-free collection type.
import java.util.SortedSet;

/**
 * TradeProposal — a formal, concrete offer sent from one user (the requester) to another (the
 * recipient). Where a TradeMatch only says "a trade is possible", a TradeProposal says "here is the
 * exact deal I am proposing, please accept or decline".
 *
 * <p>FIELDS:
 * <ul>
 *   <li>{@code proposalId} — a unique text id so the server can track this specific proposal.</li>
 *   <li>{@code requesterUsername} / {@code recipientUsername} — the two people involved.</li>
 *   <li>{@code requesterOffer} — the exact stickers the requester promises to give.</li>
 *   <li>{@code recipientOfferCandidates} — the stickers the recipient could give back.</li>
 *   <li>{@code recipientSelectionRequired} — true if the recipient still has to CHOOSE which of their
 *       candidate stickers to send (because they have more options than needed).</li>
 *   <li>{@code expectedRecipientSelectionSize} — how many stickers the recipient must end up giving.</li>
 * </ul>
 *
 * <p>KEY CONCEPT — two trade shapes: a "fixed" proposal already lists exactly what each side gives.
 * A "selection-required" proposal leaves the final choice to the recipient. The compact constructor
 * below contains careful validation so both shapes always stay internally consistent.
 *
 * <p>HOW IT INTERACTS: the server (ExchangeCoordinator) builds a TradeProposal from a
 * ProposeTradeRequest, wraps it in an IncomingTradeProposal message, and sends it to the recipient.
 */
// Immutable data holder with seven components; serializable for network transport.
public record TradeProposal(
        String proposalId,
        String requesterUsername,
        String recipientUsername,
        SortedSet<Integer> requesterOffer,
        SortedSet<Integer> recipientOfferCandidates,
        boolean recipientSelectionRequired,
        int expectedRecipientSelectionSize) implements Serializable {

    // Compact constructor: validates and normalizes everything before the proposal is stored.
    public TradeProposal {
        // Every proposal needs an id so it can be referenced later; reject a missing or blank one.
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("Trade proposals require an id.");
        }
        // Both usernames are mandatory.
        if (requesterUsername == null || requesterUsername.isBlank()) {
            throw new IllegalArgumentException("Requester username is required.");
        }
        if (recipientUsername == null || recipientUsername.isBlank()) {
            throw new IllegalArgumentException("Recipient username is required.");
        }
        // Tidy both sticker sets into safe, sorted, unmodifiable copies.
        requesterOffer = StickerSets.normalize(requesterOffer);
        recipientOfferCandidates = StickerSets.normalize(recipientOfferCandidates);
        // A trade is meaningless if either side brings nothing, so both sets must contain stickers.
        if (requesterOffer.isEmpty() || recipientOfferCandidates.isEmpty()) {
            throw new IllegalArgumentException("Trade proposals must include stickers from both users.");
        }
        // Branch on the trade shape. This "if" handles the case where the recipient must still choose.
        if (recipientSelectionRequired) {
            // If choosing is required, we must be told how many to choose, and it has to be a positive number.
            if (expectedRecipientSelectionSize <= 0) {
                throw new IllegalArgumentException("Recipient selection size must be positive.");
            }
            // The requester's offer count has to equal how many the recipient will return, so the swap is even.
            if (requesterOffer.size() != expectedRecipientSelectionSize) {
                throw new IllegalArgumentException("The requester offer must match the recipient selection size.");
            }
            // "Choosing" only makes sense if there are MORE candidates than needed; otherwise there is no choice.
            if (recipientOfferCandidates.size() <= expectedRecipientSelectionSize) {
                throw new IllegalArgumentException("Recipient selection only makes sense when there are extra choices.");
            }
        } else {
            // The "else" branch handles a FIXED proposal: nothing to choose, so the size is simply the number of
            // candidates, and we assign that value to the field. (Assigning to a field is allowed here.)
            expectedRecipientSelectionSize = recipientOfferCandidates.size();
            // A fixed trade must swap an equal number of stickers on each side.
            if (requesterOffer.size() != recipientOfferCandidates.size()) {
                throw new IllegalArgumentException("Fixed proposals must trade the same number of stickers on both sides.");
            }
        }
    }

    // Convenience accessor used only for fixed proposals: returns the recipient's stickers directly.
    public SortedSet<Integer> fixedRecipientOffer() {
        // Safety check: if the recipient still needs to choose, there is no single fixed answer to return, so
        // calling this method would be a programming mistake. IllegalStateException means "the object is not in
        // a state where this call is valid right now".
        if (recipientSelectionRequired) {
            throw new IllegalStateException("The recipient still needs to choose stickers for this trade.");
        }
        // For a fixed trade the candidates ARE the final offer, so return them.
        return recipientOfferCandidates;
    }
}
