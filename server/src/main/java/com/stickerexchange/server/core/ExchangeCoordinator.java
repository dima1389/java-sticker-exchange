package com.stickerexchange.server.core;

import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import com.stickerexchange.common.model.TradeProposal;
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
import com.stickerexchange.common.service.AlbumInitializer;
import com.stickerexchange.common.service.ExchangeCalculator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ExchangeCoordinator {
    private final AlbumInitializer albumInitializer = new AlbumInitializer();
    private final Map<String, ConnectedUser> users = new HashMap<>();
    private final Map<String, TradeProposal> proposals = new HashMap<>();

    public synchronized CommandResult register(RegisterRequest request, ClientConnection connection) {
        String username = request.username().trim();
        if (users.containsKey(username)) {
            return CommandResult.reply(new RegisterResponse(false, "Username is already in use.", null));
        }

        AlbumState initialAlbum = albumInitializer.createRandomAlbum();
        users.put(username, new ConnectedUser(username, initialAlbum, connection));
        return CommandResult.reply(new RegisterResponse(true, "Connected as " + username + ".", initialAlbum));
    }

    public synchronized CommandResult synchronizeAlbum(String username, AlbumSyncRequest request) {
        ConnectedUser user = users.get(username);
        if (user == null) {
            return CommandResult.reply(new AlbumSyncResponse(false, "Register before editing the album.", null));
        }

        user.albumState = request.albumState();
        return CommandResult.reply(new AlbumSyncResponse(true, "Album updated.", user.albumState));
    }

    public synchronized CommandResult findMatches(String username, FindMatchesRequest request) {
        ConnectedUser user = users.get(username);
        if (user == null) {
            return CommandResult.reply(new InfoMessage(false, "Register before requesting matches.", null));
        }

        Map<String, AlbumState> otherAlbums = users.values().stream()
                .filter(candidate -> !candidate.username.equals(username))
                .collect(Collectors.toMap(candidate -> candidate.username, candidate -> candidate.albumState));

        List<TradeMatch> matches = ExchangeCalculator.calculateMatches(user.albumState, otherAlbums);
        return CommandResult.reply(new MatchesResponse(matches));
    }

    public synchronized CommandResult proposeTrade(String requesterUsername, ProposeTradeRequest request) {
        ConnectedUser requester = users.get(requesterUsername);
        if (requester == null) {
            return CommandResult.reply(new InfoMessage(false, "Register before sending trade requests.", null));
        }

        if (requesterUsername.equals(request.targetUsername())) {
            return CommandResult.reply(new InfoMessage(false, "You cannot trade with yourself.", requester.albumState));
        }

        ConnectedUser recipient = users.get(request.targetUsername());
        if (recipient == null) {
            return CommandResult.reply(new InfoMessage(false, "The selected user is no longer online.", requester.albumState));
        }

        if (hasPendingProposalBetween(requesterUsername, request.targetUsername())) {
            return CommandResult.reply(new InfoMessage(false, "There is already a pending proposal between these users.", requester.albumState));
        }

        Optional<TradeMatch> liveMatch = ExchangeCalculator.calculateMatch(recipient.username, requester.albumState, recipient.albumState);
        if (liveMatch.isEmpty()) {
            return CommandResult.reply(new InfoMessage(false, "There are no valid stickers to exchange with that user anymore.", requester.albumState));
        }

        String validationError = validateProposalRequest(liveMatch.get(), request);
        if (validationError != null) {
            return CommandResult.reply(new InfoMessage(false, validationError, requester.albumState));
        }

        TradeProposal proposal = new TradeProposal(
                UUID.randomUUID().toString(),
                requesterUsername,
                recipient.username,
                request.requesterOffer(),
                request.recipientOfferCandidates(),
                request.recipientSelectionRequired(),
                request.expectedRecipientSelectionSize());
        proposals.put(proposal.proposalId(), proposal);

        List<DispatchMessage> followUps = List.of(new DispatchMessage(recipient.connection, new IncomingTradeProposal(proposal)));
        return new CommandResult(
                new InfoMessage(true, "Trade request sent to " + recipient.username + ".", requester.albumState),
                followUps);
    }

    public synchronized CommandResult respondToTrade(String recipientUsername, TradeDecisionRequest request) {
        TradeProposal proposal = proposals.get(request.proposalId());
        ConnectedUser recipient = users.get(recipientUsername);
        if (recipient == null) {
            return CommandResult.reply(new InfoMessage(false, "Register before answering trade requests.", null));
        }
        if (proposal == null) {
            return CommandResult.reply(new InfoMessage(false, "That trade request is no longer available.", recipient.albumState));
        }
        if (!proposal.recipientUsername().equals(recipientUsername)) {
            return CommandResult.reply(new InfoMessage(false, "You cannot answer someone else's trade request.", recipient.albumState));
        }

        ConnectedUser requester = users.get(proposal.requesterUsername());
        if (requester == null) {
            proposals.remove(proposal.proposalId());
            return CommandResult.reply(new InfoMessage(false, "The requester disconnected before the trade was answered.", recipient.albumState));
        }

        if (!request.accept()) {
            proposals.remove(proposal.proposalId());
            return new CommandResult(
                    new InfoMessage(true, "You declined the trade from " + requester.username + ".", recipient.albumState),
                    List.of(new DispatchMessage(requester.connection,
                            new InfoMessage(false, recipient.username + " declined your trade request.", requester.albumState))));
        }

        SortedSet<Integer> resolvedRecipientOffer = resolveRecipientOffer(proposal, request, recipient.albumState);
        if (resolvedRecipientOffer == null) {
            proposals.remove(proposal.proposalId());
            return new CommandResult(
                    new InfoMessage(false, "The selected stickers are not valid for this trade.", recipient.albumState),
                    List.of(new DispatchMessage(requester.connection,
                            new InfoMessage(false, recipient.username + " could not complete the trade because the selection was invalid.", requester.albumState))));
        }

        if (!isTradeStillValid(requester.albumState, recipient.albumState, proposal.requesterOffer(), resolvedRecipientOffer)) {
            proposals.remove(proposal.proposalId());
            return new CommandResult(
                    new InfoMessage(false, "The trade became outdated because one of the albums changed.", recipient.albumState),
                    List.of(new DispatchMessage(requester.connection,
                            new InfoMessage(false, "The trade with " + recipient.username + " became outdated and was canceled.", requester.albumState))));
        }

        requester.albumState = requester.albumState.withoutDuplicates(proposal.requesterOffer()).withoutMissing(resolvedRecipientOffer);
        recipient.albumState = recipient.albumState.withoutDuplicates(resolvedRecipientOffer).withoutMissing(proposal.requesterOffer());
        proposals.remove(proposal.proposalId());

        return new CommandResult(
                new TradeAppliedEvent(requester.username, "Trade completed with " + requester.username + ".", recipient.albumState),
                List.of(new DispatchMessage(requester.connection,
                        new TradeAppliedEvent(recipient.username, "Trade completed with " + recipient.username + ".", requester.albumState))));
    }

    public synchronized List<DispatchMessage> disconnect(String username) {
        if (username == null) {
            return List.of();
        }

        ConnectedUser removedUser = users.remove(username);
        if (removedUser == null) {
            return List.of();
        }

        List<DispatchMessage> notifications = new ArrayList<>();
        Iterator<Map.Entry<String, TradeProposal>> iterator = proposals.entrySet().iterator();
        while (iterator.hasNext()) {
            TradeProposal proposal = iterator.next().getValue();
            if (!proposal.requesterUsername().equals(username) && !proposal.recipientUsername().equals(username)) {
                continue;
            }

            iterator.remove();
            String otherUsername = proposal.requesterUsername().equals(username)
                    ? proposal.recipientUsername()
                    : proposal.requesterUsername();
            ConnectedUser otherUser = users.get(otherUsername);
            if (otherUser != null) {
                notifications.add(new DispatchMessage(otherUser.connection,
                        new InfoMessage(false, username + " disconnected. A pending trade was canceled.", otherUser.albumState)));
            }
        }
        return notifications;
    }

    private String validateProposalRequest(TradeMatch liveMatch, ProposeTradeRequest request) {
        if (!liveMatch.offerFromCurrentUser().containsAll(request.requesterOffer())) {
            return "The selected outgoing stickers are no longer available.";
        }

        if (request.recipientSelectionRequired()) {
            if (!liveMatch.otherUserMustSelect()) {
                return "The recipient does not need to choose stickers for this trade.";
            }
            if (!request.requesterOffer().equals(liveMatch.offerFromCurrentUser())) {
                return "When the other user has more stickers, you must offer all of your valid stickers.";
            }
            if (!request.recipientOfferCandidates().equals(liveMatch.offerFromOtherUser())) {
                return "The recipient selection candidates do not match the live exchange options.";
            }
            if (request.expectedRecipientSelectionSize() != liveMatch.offerFromCurrentUser().size()) {
                return "The recipient selection size does not match the current trade size.";
            }
            return null;
        }

        if (liveMatch.otherUserMustSelect()) {
            return "The other user has more available stickers and must select which ones to trade.";
        }

        if (liveMatch.currentUserMustSelect()) {
            if (request.requesterOffer().size() != liveMatch.offerFromOtherUser().size()) {
                return "Select exactly " + liveMatch.offerFromOtherUser().size() + " stickers to offer.";
            }
            if (!request.recipientOfferCandidates().equals(liveMatch.offerFromOtherUser())) {
                return "The selected recipient stickers do not match the live exchange options.";
            }
            return null;
        }

        if (!request.requesterOffer().equals(liveMatch.offerFromCurrentUser())) {
            return "This exchange should include all currently matching outgoing stickers.";
        }
        if (!request.recipientOfferCandidates().equals(liveMatch.offerFromOtherUser())) {
            return "This exchange should include all currently matching incoming stickers.";
        }
        return null;
    }

    private boolean hasPendingProposalBetween(String leftUsername, String rightUsername) {
        Set<String> pair = Set.of(leftUsername, rightUsername);
        return proposals.values().stream()
                .anyMatch(proposal -> Set.of(proposal.requesterUsername(), proposal.recipientUsername()).equals(pair));
    }

    private SortedSet<Integer> resolveRecipientOffer(
            TradeProposal proposal,
            TradeDecisionRequest request,
            AlbumState recipientAlbum) {
        SortedSet<Integer> selectedRecipientOffer = new TreeSet<>();
        if (proposal.recipientSelectionRequired()) {
            selectedRecipientOffer.addAll(request.selectedRecipientOffer());
            if (selectedRecipientOffer.size() != proposal.expectedRecipientSelectionSize()) {
                return null;
            }
            if (!proposal.recipientOfferCandidates().containsAll(selectedRecipientOffer)) {
                return null;
            }
        } else {
            selectedRecipientOffer.addAll(proposal.fixedRecipientOffer());
        }

        return recipientAlbum.duplicatesContainAll(selectedRecipientOffer) ? selectedRecipientOffer : null;
    }

    private boolean isTradeStillValid(
            AlbumState requesterAlbum,
            AlbumState recipientAlbum,
            SortedSet<Integer> requesterOffer,
            SortedSet<Integer> recipientOffer) {
        return requesterAlbum.duplicatesContainAll(requesterOffer)
                && recipientAlbum.missingContainAll(requesterOffer)
                && recipientAlbum.duplicatesContainAll(recipientOffer)
                && requesterAlbum.missingContainAll(recipientOffer);
    }

    private static final class ConnectedUser {
        private final String username;
        private final ClientConnection connection;
        private AlbumState albumState;

        private ConnectedUser(String username, AlbumState albumState, ClientConnection connection) {
            this.username = Objects.requireNonNull(username, "Username is required.");
            this.albumState = Objects.requireNonNull(albumState, "Album state is required.");
            this.connection = Objects.requireNonNull(connection, "Connection is required.");
        }
    }

    public interface ClientConnection {
        void send(Message message);
    }

    public record DispatchMessage(ClientConnection connection, Message message) {
        public DispatchMessage {
            Objects.requireNonNull(connection, "Connection is required.");
            Objects.requireNonNull(message, "Message is required.");
        }
    }

    public record CommandResult(Message directMessage, List<DispatchMessage> followUpMessages) {
        public CommandResult {
            followUpMessages = List.copyOf(followUpMessages);
        }

        public static CommandResult reply(Message directMessage) {
            return new CommandResult(directMessage, List.of());
        }
    }
}
