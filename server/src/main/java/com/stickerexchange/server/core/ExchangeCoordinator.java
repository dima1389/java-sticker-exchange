// The "server.core" package holds the server's central business logic (the rules of the game).
package com.stickerexchange.server.core;

// The shared data objects (model) we read and update.
import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import com.stickerexchange.common.model.TradeProposal;
// Every network message type this class sends or reacts to.
import com.stickerexchange.common.protocol.AlbumSyncRequest;
import com.stickerexchange.common.protocol.AlbumSyncResponse;
import com.stickerexchange.common.protocol.FindMatchesRequest;
import com.stickerexchange.common.protocol.IncomingTradeProposal;
import com.stickerexchange.common.protocol.InfoMessage;
import com.stickerexchange.common.protocol.Message;
import com.stickerexchange.common.protocol.MatchesResponse;
import com.stickerexchange.common.protocol.ProposeTradeRequest;
import com.stickerexchange.common.protocol.RegisterRequest;
import com.stickerexchange.common.protocol.RegisterResponse;
import com.stickerexchange.common.protocol.TradeAppliedEvent;
import com.stickerexchange.common.protocol.TradeDecisionRequest;
// The helper services for building albums and computing matches.
import com.stickerexchange.common.service.AlbumInitializer;
import com.stickerexchange.common.service.ExchangeCalculator;
// Standard collection and utility classes used throughout this file.
import java.util.ArrayList;   // resizable list
import java.util.HashMap;     // fast key -> value lookup table
import java.util.Iterator;    // lets us walk through a collection and safely remove items
import java.util.List;        // ordered collection interface
import java.util.Map;         // key -> value lookup interface
import java.util.Objects;     // null-check helpers
import java.util.Optional;    // a box that may or may not hold a value
import java.util.Set;         // a collection with no duplicates
import java.util.SortedSet;   // a sorted, duplicate-free collection
import java.util.TreeSet;     // concrete sorted set
import java.util.UUID;        // generates unique ids
import java.util.stream.Collectors; // recipes for collecting streams

/**
 * ExchangeCoordinator — the SERVER'S BRAIN. It keeps track of every connected user, every user's
 * album, and every pending trade proposal, and it enforces all the rules of trading.
 *
 * <p>WHAT IT DOES: ServerMain's ClientHandlers forward each client message here. This class decides
 * what should happen (register a user, save an album, find matches, propose a trade, answer a trade,
 * handle a disconnect) and returns a {@link CommandResult} describing which messages to send back and
 * to whom.
 *
 * <p>KEY CONCEPT — shared mutable state and thread safety: because many ClientHandler threads (one per
 * user) call into this single object at the same time, every public method is marked
 * {@code synchronized}. That keyword makes Java allow only ONE thread inside these methods at a time,
 * preventing two trades from corrupting the data simultaneously.
 *
 * <p>KEY CONCEPT — two-phase trade safety: a trade is only applied after re-checking that both albums
 * STILL contain the right stickers (they might have changed since the proposal was made). This guards
 * against stale, invalid trades.
 */
// Central logic class. "final" = not meant to be extended.
public final class ExchangeCoordinator {
    // Creates the random starter album for each new user. "final" = this reference never changes.
    private final AlbumInitializer albumInitializer = new AlbumInitializer();
    // A lookup table mapping each username to that user's connection + album. This is the list of who is online.
    private final Map<String, ConnectedUser> users = new HashMap<>();
    // A lookup table of all in-flight trade proposals, keyed by their unique proposal id.
    private final Map<String, TradeProposal> proposals = new HashMap<>();

    // Registers a new user. "synchronized" = thread-safe. Returns a CommandResult to send back.
    public synchronized CommandResult register(RegisterRequest request, ClientConnection connection) {
        // Clean up the requested username by trimming surrounding spaces.
        String username = request.username().trim();
        // "containsKey" checks whether that username is already taken. Names must be unique.
        if (users.containsKey(username)) {
            // Reply with a failure RegisterResponse (null album because registration did not happen).
            return CommandResult.reply(new RegisterResponse(false, "Username is already in use.", null));
        }

        // Build a fresh random album for the newcomer.
        AlbumState initialAlbum = albumInitializer.createRandomAlbum();
        // Record the user as online by putting them in the "users" map, storing name, album, and connection.
        users.put(username, new ConnectedUser(username, initialAlbum, connection));
        // Reply with success and hand back the starter album so the client can display it.
        return CommandResult.reply(new RegisterResponse(true, "Connected as " + username + ".", initialAlbum));
    }

    // Saves an updated album for an already-registered user.
    public synchronized CommandResult synchronizeAlbum(String username, AlbumSyncRequest request) {
        // Look up the user. "get" returns null if the name is not online.
        ConnectedUser user = users.get(username);
        // Guard: cannot save an album for someone who never registered.
        if (user == null) {
            return CommandResult.reply(new AlbumSyncResponse(false, "Register before editing the album.", null));
        }

        // Overwrite the server's stored album with the one the client sent.
        user.albumState = request.albumState();
        // Confirm success and echo the saved album back.
        return CommandResult.reply(new AlbumSyncResponse(true, "Album updated.", user.albumState));
    }

    // Finds every trade the given user could currently make with the other online users.
    public synchronized CommandResult findMatches(String username, FindMatchesRequest request) {
        // Look up the requesting user.
        ConnectedUser user = users.get(username);
        if (user == null) {
            return CommandResult.reply(new InfoMessage(false, "Register before requesting matches.", null));
        }

        // Build a map of EVERY OTHER user's name to their album, using a stream pipeline:
        Map<String, AlbumState> otherAlbums = users.values().stream()
                // ".filter(...)" keeps only users whose name is NOT the requester's (we don't match against ourselves).
                .filter(candidate -> !candidate.username.equals(username))
                // ".collect(Collectors.toMap(keyFn, valueFn))" gathers the survivors into a Map: username -> album.
                .collect(Collectors.toMap(candidate -> candidate.username, candidate -> candidate.albumState));

        // Ask the calculator for all matches between this user's album and everyone else's.
        List<TradeMatch> matches = ExchangeCalculator.calculateMatches(user.albumState, otherAlbums);
        // Send the list of matches back to the client.
        return CommandResult.reply(new MatchesResponse(matches));
    }

    // Handles a user's request to propose a trade to someone else. Performs many safety checks first.
    public synchronized CommandResult proposeTrade(String requesterUsername, ProposeTradeRequest request) {
        // Look up the person making the proposal.
        ConnectedUser requester = users.get(requesterUsername);
        if (requester == null) {
            return CommandResult.reply(new InfoMessage(false, "Register before sending trade requests.", null));
        }

        // You cannot trade with yourself; ".equals" compares the two usernames for being the same text.
        if (requesterUsername.equals(request.targetUsername())) {
            return CommandResult.reply(new InfoMessage(false, "You cannot trade with yourself.", requester.albumState));
        }

        // Look up the intended trade partner.
        ConnectedUser recipient = users.get(request.targetUsername());
        if (recipient == null) {
            // They may have logged off since the matches list was shown.
            return CommandResult.reply(new InfoMessage(false, "The selected user is no longer online.", requester.albumState));
        }

        // Only one live proposal is allowed between any two people at once, to avoid confusing duplicates.
        if (hasPendingProposalBetween(requesterUsername, request.targetUsername())) {
            return CommandResult.reply(new InfoMessage(false, "There is already a pending proposal between these users.", requester.albumState));
        }

        // Re-compute the trade from the CURRENT albums (albums can change after the client saw them).
        Optional<TradeMatch> liveMatch = ExchangeCalculator.calculateMatch(recipient.username, requester.albumState, recipient.albumState);
        // "isEmpty()" on the Optional is true when no trade is possible anymore.
        if (liveMatch.isEmpty()) {
            return CommandResult.reply(new InfoMessage(false, "There are no valid stickers to exchange with that user anymore.", requester.albumState));
        }

        // Check the specific stickers the client chose are consistent with the live match. Returns an error
        // message String, or null when everything is valid. ".get()" unwraps the value inside the Optional.
        String validationError = validateProposalRequest(liveMatch.get(), request);
        if (validationError != null) {
            return CommandResult.reply(new InfoMessage(false, validationError, requester.albumState));
        }

        // All checks passed: build the official TradeProposal, giving it a brand-new unique id.
        TradeProposal proposal = new TradeProposal(
                // "UUID.randomUUID().toString()" generates a practically-unique random id as text.
                UUID.randomUUID().toString(),
                requesterUsername,
                recipient.username,
                request.requesterOffer(),
                request.recipientOfferCandidates(),
                request.recipientSelectionRequired(),
                request.expectedRecipientSelectionSize());
        // Remember the proposal so we can find it again when the recipient answers.
        proposals.put(proposal.proposalId(), proposal);

        // Prepare a follow-up message that DELIVERS the proposal to the recipient's connection.
        List<DispatchMessage> followUps = List.of(new DispatchMessage(recipient.connection, new IncomingTradeProposal(proposal)));
        // Return: a direct confirmation to the requester PLUS the follow-up to the recipient.
        return new CommandResult(
                new InfoMessage(true, "Trade request sent to " + recipient.username + ".", requester.albumState),
                followUps);
    }

    // Handles the recipient's answer (accept/decline) to a trade proposal. If accepted and valid, it
    // actually swaps the stickers between the two albums.
    public synchronized CommandResult respondToTrade(String recipientUsername, TradeDecisionRequest request) {
        // Find the proposal being answered by its id (may be null if it expired or was already handled).
        TradeProposal proposal = proposals.get(request.proposalId());
        // Find the user who is answering.
        ConnectedUser recipient = users.get(recipientUsername);
        if (recipient == null) {
            return CommandResult.reply(new InfoMessage(false, "Register before answering trade requests.", null));
        }
        // If the proposal is gone, there is nothing to answer.
        if (proposal == null) {
            return CommandResult.reply(new InfoMessage(false, "That trade request is no longer available.", recipient.albumState));
        }
        // Security check: a user may only answer a proposal that was actually addressed to THEM.
        if (!proposal.recipientUsername().equals(recipientUsername)) {
            return CommandResult.reply(new InfoMessage(false, "You cannot answer someone else's trade request.", recipient.albumState));
        }

        // Look up the original requester, who must still be online to complete the trade.
        ConnectedUser requester = users.get(proposal.requesterUsername());
        if (requester == null) {
            // Requester left: drop the proposal and inform the recipient.
            proposals.remove(proposal.proposalId());
            return CommandResult.reply(new InfoMessage(false, "The requester disconnected before the trade was answered.", recipient.albumState));
        }

        // Branch: the recipient DECLINED (request.accept() is false; "!" flips it to true here).
        if (!request.accept()) {
            // Remove the proposal and notify both sides that it was declined.
            proposals.remove(proposal.proposalId());
            return new CommandResult(
                    // Confirmation shown to the recipient who declined.
                    new InfoMessage(true, "You declined the trade from " + requester.username + ".", recipient.albumState),
                    // Follow-up telling the original requester their offer was declined.
                    List.of(new DispatchMessage(requester.connection,
                            new InfoMessage(false, recipient.username + " declined your trade request.", requester.albumState))));
        }

        // The recipient ACCEPTED. Work out exactly which stickers they are giving (handling the case where
        // they had to choose). Returns null if their selection is invalid.
        SortedSet<Integer> resolvedRecipientOffer = resolveRecipientOffer(proposal, request, recipient.albumState);
        if (resolvedRecipientOffer == null) {
            // Invalid selection: cancel and tell both users.
            proposals.remove(proposal.proposalId());
            return new CommandResult(
                    new InfoMessage(false, "The selected stickers are not valid for this trade.", recipient.albumState),
                    List.of(new DispatchMessage(requester.connection,
                            new InfoMessage(false, recipient.username + " could not complete the trade because the selection was invalid.", requester.albumState))));
        }

        // Re-verify the trade is STILL doable against both current albums (they may have changed meanwhile).
        if (!isTradeStillValid(requester.albumState, recipient.albumState, proposal.requesterOffer(), resolvedRecipientOffer)) {
            // Stale trade: cancel and notify both sides.
            proposals.remove(proposal.proposalId());
            return new CommandResult(
                    new InfoMessage(false, "The trade became outdated because one of the albums changed.", recipient.albumState),
                    List.of(new DispatchMessage(requester.connection,
                            new InfoMessage(false, "The trade with " + recipient.username + " became outdated and was canceled.", requester.albumState))));
        }

        // Everything checks out — perform the swap by building NEW albums (records are immutable). The requester
        // loses the duplicates they gave and is no longer missing what they received; the recipient mirrors that.
        // The two ".withoutX(...)" calls are "chained": the result of the first feeds into the second.
        requester.albumState = requester.albumState.withoutDuplicates(proposal.requesterOffer()).withoutMissing(resolvedRecipientOffer);
        recipient.albumState = recipient.albumState.withoutDuplicates(resolvedRecipientOffer).withoutMissing(proposal.requesterOffer());
        // The proposal is fulfilled; remove it.
        proposals.remove(proposal.proposalId());

        // Tell both users the trade completed, sending each their freshly updated album.
        return new CommandResult(
                new TradeAppliedEvent(requester.username, "Trade completed with " + requester.username + ".", recipient.albumState),
                List.of(new DispatchMessage(requester.connection,
                        new TradeAppliedEvent(recipient.username, "Trade completed with " + recipient.username + ".", requester.albumState))));
    }

    // Cleans up when a user leaves: removes them, cancels their pending trades, and notifies partners.
    public synchronized List<DispatchMessage> disconnect(String username) {
        // If we never knew the username (e.g. the client disconnected before registering), nothing to do.
        // "List.of()" returns an empty, unmodifiable list.
        if (username == null) {
            return List.of();
        }

        // Remove the user from the online list. "remove" returns the removed value, or null if absent.
        ConnectedUser removedUser = users.remove(username);
        if (removedUser == null) {
            return List.of();
        }

        // We will collect "this trade was canceled" notifications to send to the leaver's trade partners.
        List<DispatchMessage> notifications = new ArrayList<>();
        // An Iterator lets us loop through the proposals AND safely delete entries as we go (a normal for-each
        // loop would crash if you removed items mid-loop).
        Iterator<Map.Entry<String, TradeProposal>> iterator = proposals.entrySet().iterator();
        // "hasNext()" is true while there are more entries to examine.
        while (iterator.hasNext()) {
            // Move to the next entry and grab its proposal value.
            TradeProposal proposal = iterator.next().getValue();
            // Skip proposals that have nothing to do with the leaving user. "&&" means AND; "!" means NOT.
            if (!proposal.requesterUsername().equals(username) && !proposal.recipientUsername().equals(username)) {
                continue;
            }

            // This proposal involves the leaver, so cancel it by removing it through the iterator.
            iterator.remove();
            // Work out who the OTHER party was, using a ternary "condition ? valueIfTrue : valueIfFalse".
            // If the leaver was the requester, the partner is the recipient; otherwise it's the requester.
            String otherUsername = proposal.requesterUsername().equals(username)
                    ? proposal.recipientUsername()
                    : proposal.requesterUsername();
            // Find that partner if they are still online.
            ConnectedUser otherUser = users.get(otherUsername);
            if (otherUser != null) {
                // Queue a message letting the partner know the pending trade was canceled.
                notifications.add(new DispatchMessage(otherUser.connection,
                        new InfoMessage(false, username + " disconnected. A pending trade was canceled.", otherUser.albumState)));
            }
        }
        // Return all the cancellation notices so the caller can deliver them.
        return notifications;
    }

    // Private helper that checks a proposal request against the live match. Returns an error message String
    // if something is wrong, or null when the request is acceptable. (Returning null = "no error".)
    private String validateProposalRequest(TradeMatch liveMatch, ProposeTradeRequest request) {
        // The stickers the requester wants to give must all still be among their currently-offerable stickers.
        if (!liveMatch.offerFromCurrentUser().containsAll(request.requesterOffer())) {
            return "The selected outgoing stickers are no longer available.";
        }

        // Case A: the request says the RECIPIENT must choose which stickers to give back.
        if (request.recipientSelectionRequired()) {
            // ...but that only makes sense if the live match actually requires the other user to select.
            if (!liveMatch.otherUserMustSelect()) {
                return "The recipient does not need to choose stickers for this trade.";
            }
            // When the recipient has more stickers, the requester must offer ALL of their valid stickers.
            if (!request.requesterOffer().equals(liveMatch.offerFromCurrentUser())) {
                return "When the other user has more stickers, you must offer all of your valid stickers.";
            }
            // The pool of candidates the recipient may pick from must match the live options exactly.
            if (!request.recipientOfferCandidates().equals(liveMatch.offerFromOtherUser())) {
                return "The recipient selection candidates do not match the live exchange options.";
            }
            // The number the recipient must pick has to equal the requester's offer size (an even swap).
            if (request.expectedRecipientSelectionSize() != liveMatch.offerFromCurrentUser().size()) {
                return "The recipient selection size does not match the current trade size.";
            }
            // All good for this case: return null meaning "no error".
            return null;
        }

        // Case B: the request is NOT a recipient-selection trade, but the live match says the other user has
        // more stickers and therefore SHOULD have to select. That is a contradiction, so reject it.
        if (liveMatch.otherUserMustSelect()) {
            return "The other user has more available stickers and must select which ones to trade.";
        }

        // Case C: the CURRENT user has the bigger pile and must select which of their stickers to give.
        if (liveMatch.currentUserMustSelect()) {
            // They must offer exactly as many stickers as the recipient can give back.
            if (request.requesterOffer().size() != liveMatch.offerFromOtherUser().size()) {
                return "Select exactly " + liveMatch.offerFromOtherUser().size() + " stickers to offer.";
            }
            // The recipient stickers requested must match the live options exactly.
            if (!request.recipientOfferCandidates().equals(liveMatch.offerFromOtherUser())) {
                return "The selected recipient stickers do not match the live exchange options.";
            }
            return null;
        }

        // Case D: a perfectly even trade where neither side chooses. Both offers must match the live match fully.
        if (!request.requesterOffer().equals(liveMatch.offerFromCurrentUser())) {
            return "This exchange should include all currently matching outgoing stickers.";
        }
        if (!request.recipientOfferCandidates().equals(liveMatch.offerFromOtherUser())) {
            return "This exchange should include all currently matching incoming stickers.";
        }
        // Nothing was wrong: signal success with null.
        return null;
    }

    // Returns true if any pending proposal already exists between these two users (in either direction).
    private boolean hasPendingProposalBetween(String leftUsername, String rightUsername) {
        // "Set.of(a, b)" builds an unordered pair. Using a Set means {Alice, Bob} equals {Bob, Alice},
        // so direction does not matter when we compare.
        Set<String> pair = Set.of(leftUsername, rightUsername);
        // Stream over every existing proposal and check if any one connects exactly this pair of users.
        return proposals.values().stream()
                // "anyMatch" returns true as soon as ONE proposal satisfies the test. We compare the proposal's
                // {requester, recipient} pair against our target pair.
                .anyMatch(proposal -> Set.of(proposal.requesterUsername(), proposal.recipientUsername()).equals(pair));
    }

    // Works out the exact set of stickers the recipient will give. Returns null if the choice is invalid.
    private SortedSet<Integer> resolveRecipientOffer(
            TradeProposal proposal,
            TradeDecisionRequest request,
            AlbumState recipientAlbum) {
        // Start with an empty sorted set we will fill in.
        SortedSet<Integer> selectedRecipientOffer = new TreeSet<>();
        // If the proposal required the recipient to choose...
        if (proposal.recipientSelectionRequired()) {
            // ...take the stickers they actually selected.
            selectedRecipientOffer.addAll(request.selectedRecipientOffer());
            // The count they chose must equal what the proposal expects, otherwise it is invalid (return null).
            if (selectedRecipientOffer.size() != proposal.expectedRecipientSelectionSize()) {
                return null;
            }
            // Every chosen sticker must be one of the allowed candidates, otherwise invalid.
            if (!proposal.recipientOfferCandidates().containsAll(selectedRecipientOffer)) {
                return null;
            }
        } else {
            // No choice needed: the recipient gives the fixed set defined by the proposal.
            selectedRecipientOffer.addAll(proposal.fixedRecipientOffer());
        }

        // Final guard: the recipient must really own (as duplicates) all the stickers they are about to give.
        // The ternary returns the offer if valid, or null if they do not actually have those stickers.
        return recipientAlbum.duplicatesContainAll(selectedRecipientOffer) ? selectedRecipientOffer : null;
    }

    // Re-checks, right before applying a trade, that BOTH albums can still support the swap. Returns true only
    // if every condition holds (the "&&" chain requires all four to be true).
    private boolean isTradeStillValid(
            AlbumState requesterAlbum,
            AlbumState recipientAlbum,
            SortedSet<Integer> requesterOffer,
            SortedSet<Integer> recipientOffer) {
        // The requester still owns the duplicates they promised, AND
        return requesterAlbum.duplicatesContainAll(requesterOffer)
                // the recipient is still missing those same stickers (so receiving them helps), AND
                && recipientAlbum.missingContainAll(requesterOffer)
                // the recipient still owns the duplicates they promised back, AND
                && recipientAlbum.duplicatesContainAll(recipientOffer)
                // the requester is still missing those stickers.
                && requesterAlbum.missingContainAll(recipientOffer);
    }

    // A private nested class bundling everything we know about one online user: their name, connection, and
    // current album. "static" = it does not need an outer ExchangeCoordinator instance.
    private static final class ConnectedUser {
        // The user's unique name. "final" = never changes once set.
        private final String username;
        // The channel used to send messages to this user.
        private final ClientConnection connection;
        // The user's album. NOT final because it changes as they edit it or complete trades.
        private AlbumState albumState;

        // Constructor: stores the three pieces of data, rejecting any null with a clear message.
        private ConnectedUser(String username, AlbumState albumState, ClientConnection connection) {
            this.username = Objects.requireNonNull(username, "Username is required.");
            this.albumState = Objects.requireNonNull(albumState, "Album state is required.");
            this.connection = Objects.requireNonNull(connection, "Connection is required.");
        }
    }

    // A small public interface describing "something we can send a Message to" (implemented by the server's
    // ClientHandler). Keeping it abstract lets the coordinator stay independent of networking details.
    public interface ClientConnection {
        // The single capability: deliver a message to this connection.
        void send(Message message);
    }

    // A record pairing a target connection with the message meant for it — used for messages aimed at OTHER
    // users (the "follow-ups").
    public record DispatchMessage(ClientConnection connection, Message message) {
        // Compact constructor: both parts are mandatory.
        public DispatchMessage {
            Objects.requireNonNull(connection, "Connection is required.");
            Objects.requireNonNull(message, "Message is required.");
        }
    }

    // The standard return type of the coordinator's methods. It bundles:
    //   - directMessage: the reply to send to the user who made the request (may be null), and
    //   - followUpMessages: extra messages aimed at other users (may be empty).
    public record CommandResult(Message directMessage, List<DispatchMessage> followUpMessages) {
        // Compact constructor: defensively copy the follow-up list into an unmodifiable one.
        public CommandResult {
            followUpMessages = List.copyOf(followUpMessages);
        }

        // Convenience factory for the common case of "just one reply, no follow-ups". "List.of()" is empty.
        public static CommandResult reply(Message directMessage) {
            return new CommandResult(directMessage, List.of());
        }
    }
}
