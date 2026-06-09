package com.stickerexchange.client.controller;

import com.stickerexchange.client.net.ServerConnection;
import com.stickerexchange.client.ui.StickerExchangeFrame;
import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import com.stickerexchange.common.model.TradeProposal;
import com.stickerexchange.common.protocol.AlbumSyncRequest;
import com.stickerexchange.common.protocol.AlbumSyncResponse;
import com.stickerexchange.common.protocol.IncomingTradeProposal;
import com.stickerexchange.common.protocol.InfoMessage;
import com.stickerexchange.common.protocol.MatchesResponse;
import com.stickerexchange.common.protocol.Message;
import com.stickerexchange.common.protocol.ProposeTradeRequest;
import com.stickerexchange.common.protocol.RegisterRequest;
import com.stickerexchange.common.protocol.RegisterResponse;
import com.stickerexchange.common.protocol.TradeAppliedEvent;
import com.stickerexchange.common.protocol.TradeDecisionRequest;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.SwingUtilities;

public final class ClientController implements ServerConnection.MessageListener {
    private final StickerExchangeFrame frame;

    private ServerConnection connection;
    private String username;
    private AlbumState currentAlbum = AlbumState.empty();
    private boolean connected;

    public ClientController(StickerExchangeFrame frame) {
        this.frame = frame;
    }

    public void connect(String host, int port, String requestedUsername) {
        if (connected) {
            frame.setStatus("Already connected.");
            return;
        }
        if (port <= 0) {
            frame.setStatus("Choose a valid port before connecting.");
            return;
        }
        if (requestedUsername == null || requestedUsername.isBlank()) {
            frame.showError("Username is required.");
            return;
        }

        try {
            connection = new ServerConnection(host, port, this);
            connection.connect();
            username = requestedUsername.trim();
            frame.setStatus("Connected. Registering as " + username + "...");
            connection.send(new RegisterRequest(username));
        } catch (IOException exception) {
            frame.showError("Could not connect to the server: " + exception.getMessage());
            frame.setStatus("Connection failed.");
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }
    }

    public void saveAlbum(AlbumState albumState) {
        if (!connected) {
            frame.showError("Connect to the server before editing the album.");
            return;
        }
        send(new AlbumSyncRequest(albumState), "Saving album...");
    }

    public void refreshMatches() {
        if (!connected) {
            frame.showError("Connect to the server before requesting matches.");
            return;
        }
        send(new com.stickerexchange.common.protocol.FindMatchesRequest(), "Refreshing matches...");
    }

    public void proposeTrade(TradeMatch match) {
        if (!connected) {
            frame.showError("Connect to the server before proposing trades.");
            return;
        }
        if (match == null) {
            frame.showError("Select a user from the matches list first.");
            return;
        }

        SortedSet<Integer> requesterOffer = new TreeSet<>(match.offerFromCurrentUser());
        if (match.currentUserMustSelect()) {
            SortedSet<Integer> selectedOffer = frame.selectOutgoingStickers(match);
            if (selectedOffer == null) {
                frame.setStatus("Trade proposal canceled.");
                return;
            }
            requesterOffer = selectedOffer;
        }

        boolean recipientSelectionRequired = match.otherUserMustSelect();
        ProposeTradeRequest request = new ProposeTradeRequest(
                match.otherUsername(),
                requesterOffer,
                match.offerFromOtherUser(),
                recipientSelectionRequired,
                requesterOffer.size());
        send(request, "Sending trade proposal to " + match.otherUsername() + "...");
    }

    @Override
    public void onMessageReceived(Message message) {
        SwingUtilities.invokeLater(() -> handleMessage(message));
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            connected = false;
            frame.setConnectedState(false);
            frame.setStatus("Disconnected: " + reason);
        });
    }

    private void handleMessage(Message message) {
        switch (message) {
            case RegisterResponse response -> handleRegisterResponse(response);
            case AlbumSyncResponse response -> handleAlbumSyncResponse(response);
            case MatchesResponse response -> handleMatchesResponse(response);
            case IncomingTradeProposal proposal -> handleIncomingProposal(proposal.proposal());
            case TradeAppliedEvent event -> handleTradeApplied(event);
            case InfoMessage infoMessage -> handleInfoMessage(infoMessage);
            default -> frame.setStatus("Received unsupported message from the server.");
        }
    }

    private void handleRegisterResponse(RegisterResponse response) {
        if (!response.success()) {
            frame.showError(response.message());
            frame.setStatus(response.message());
            return;
        }

        connected = true;
        currentAlbum = response.albumState();
        frame.setConnectedState(true);
        frame.showAlbum(currentAlbum);
        frame.setStatus(response.message());
        refreshMatches();
    }

    private void handleAlbumSyncResponse(AlbumSyncResponse response) {
        if (response.albumState() != null) {
            currentAlbum = response.albumState();
            frame.showAlbum(currentAlbum);
        }
        frame.setStatus(response.message());
        if (!response.success()) {
            frame.showError(response.message());
        }
        if (response.success()) {
            refreshMatches();
        }
    }

    private void handleMatchesResponse(MatchesResponse response) {
        frame.showMatches(response.matches());
        frame.setStatus("Found " + response.matches().size() + " possible trade partner(s).");
    }

    private void handleIncomingProposal(TradeProposal proposal) {
        StickerExchangeFrame.ProposalResponse answer = frame.answerTradeProposal(proposal);
        send(new TradeDecisionRequest(proposal.proposalId(), answer.accept(), answer.selectedOffer()),
                answer.accept()
                        ? "Sending trade decision..."
                        : "Declining trade from " + proposal.requesterUsername() + "...");
    }

    private void handleTradeApplied(TradeAppliedEvent event) {
        currentAlbum = event.albumState();
        frame.showAlbum(currentAlbum);
        frame.setStatus(event.message());
        frame.showInfo(event.message());
        refreshMatches();
    }

    private void handleInfoMessage(InfoMessage infoMessage) {
        if (infoMessage.albumState() != null) {
            currentAlbum = infoMessage.albumState();
            frame.showAlbum(currentAlbum);
        }
        frame.setStatus(infoMessage.message());
        if (!infoMessage.success()) {
            frame.showError(infoMessage.message());
        }
    }

    private void send(Message message, String statusMessage) {
        try {
            connection.send(message);
            frame.setStatus(statusMessage);
        } catch (IOException exception) {
            connected = false;
            frame.setConnectedState(false);
            frame.showError("Connection to the server failed: " + exception.getMessage());
            frame.setStatus("Disconnected.");
        }
    }
}
