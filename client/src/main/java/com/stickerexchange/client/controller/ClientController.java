// The "client.controller" package holds the client's CONTROLLER (the logic layer of MVC).
package com.stickerexchange.client.controller;

// The networking layer and the window this controller drives.
import com.stickerexchange.client.net.ServerConnection;
import com.stickerexchange.client.ui.StickerExchangeFrame;
// Shared model data types.
import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import com.stickerexchange.common.model.TradeProposal;
// Every protocol message the controller sends or receives.
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
// Standard library types used below.
import java.io.IOException;        // network error type
import java.util.SortedSet;        // sorted, duplicate-free collection
import java.util.TreeSet;          // concrete sorted set
import javax.swing.SwingUtilities; // helper for running code on the UI thread

/**
 * ClientController — the CONTROLLER in the client's MVC design. It sits between the window (View) and
 * the server (via ServerConnection), translating user actions into network requests and incoming
 * server messages into screen updates.
 *
 * <p>KEY CONCEPT — implementing an interface: this class declares
 * {@code implements ServerConnection.MessageListener}, which means it PROMISES to provide the two
 * callback methods the network layer needs ({@code onMessageReceived} and {@code onDisconnected}).
 * That is how server messages are delivered here.
 *
 * <p>KEY CONCEPT — the UI thread: Swing windows may only be updated from one special thread (the Event
 * Dispatch Thread). Network messages arrive on a background thread, so we use
 * {@code SwingUtilities.invokeLater(...)} to hop back onto the UI thread before touching the window.
 */
// Controller class; "final" prevents extension. It listens for messages from the ServerConnection.
public final class ClientController implements ServerConnection.MessageListener {
    // The window this controller updates. "final" = set once in the constructor.
    private final StickerExchangeFrame frame;

    // The live network connection (created when the user connects; null before that).
    private ServerConnection connection;
    // This client's username once registered.
    private String username;
    // The user's current album. It starts empty and is replaced as the server sends updates.
    private AlbumState currentAlbum = AlbumState.empty();
    // Tracks whether we are currently connected and registered. "boolean" defaults to false.
    private boolean connected;

    // Constructor: store the window so the controller can drive it later.
    public ClientController(StickerExchangeFrame frame) {
        this.frame = frame;
    }

    // Called when the user clicks "Connect". Validates input, opens the connection, and registers.
    public void connect(String host, int port, String requestedUsername) {
        // Prevent connecting twice.
        if (connected) {
            frame.setStatus("Already connected.");
            return;
        }
        // A port must be a positive number to be valid.
        if (port <= 0) {
            frame.setStatus("Choose a valid port before connecting.");
            return;
        }
        // A username is required; "isBlank()" is true for empty/whitespace-only text.
        if (requestedUsername == null || requestedUsername.isBlank()) {
            frame.showError("Username is required.");
            return;
        }

        // Opening a network connection can fail, so wrap it in try/catch.
        try {
            // Create the connection, passing "this" as the listener so incoming messages come back to us.
            connection = new ServerConnection(host, port, this);
            // Actually dial the server and start the reader thread.
            connection.connect();
            // Remember the (trimmed) username we are registering under.
            username = requestedUsername.trim();
            // Update the status bar to tell the user what is happening.
            frame.setStatus("Connected. Registering as " + username + "...");
            // Send the first message: a request to register under our username.
            connection.send(new RegisterRequest(username));
        } catch (IOException exception) {
            // Connection failed: show the error and reset the status.
            frame.showError("Could not connect to the server: " + exception.getMessage());
            frame.setStatus("Connection failed.");
            // Clean up any half-open connection so we can try again cleanly.
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }
    }

    // Called when the user clicks "Save album". Sends the edited album to the server.
    public void saveAlbum(AlbumState albumState) {
        // Must be connected to save anything.
        if (!connected) {
            frame.showError("Connect to the server before editing the album.");
            return;
        }
        // Wrap the album in a sync request and send it, updating the status bar.
        send(new AlbumSyncRequest(albumState), "Saving album...");
    }

    // Called when the user clicks "Find matches". Asks the server for current trade opportunities.
    public void refreshMatches() {
        if (!connected) {
            frame.showError("Connect to the server before requesting matches.");
            return;
        }
        // Send an (empty) FindMatchesRequest. The fully-qualified name is used here instead of an import.
        send(new com.stickerexchange.common.protocol.FindMatchesRequest(), "Refreshing matches...");
    }

    // Called when the user clicks "Propose trade" with a selected match.
    public void proposeTrade(TradeMatch match) {
        if (!connected) {
            frame.showError("Connect to the server before proposing trades.");
            return;
        }
        // The user must have picked a match from the list first.
        if (match == null) {
            frame.showError("Select a user from the matches list first.");
            return;
        }

        // Start with the full set of stickers we could offer (a copy we can adjust).
        SortedSet<Integer> requesterOffer = new TreeSet<>(match.offerFromCurrentUser());
        // If we have MORE offerable stickers than they need, we must choose exactly which to give.
        if (match.currentUserMustSelect()) {
            // Pop up a dialog asking the user to choose. It returns null if they cancel.
            SortedSet<Integer> selectedOffer = frame.selectOutgoingStickers(match);
            if (selectedOffer == null) {
                frame.setStatus("Trade proposal canceled.");
                return;
            }
            // Use the chosen subset as our offer.
            requesterOffer = selectedOffer;
        }

        // If the OTHER user has more stickers than needed, THEY will have to choose, so flag that.
        boolean recipientSelectionRequired = match.otherUserMustSelect();
        // Build the proposal request describing the whole deal.
        ProposeTradeRequest request = new ProposeTradeRequest(
                match.otherUsername(),
                requesterOffer,
                match.offerFromOtherUser(),
                recipientSelectionRequired,
                requesterOffer.size());
        // Send it, updating the status bar.
        send(request, "Sending trade proposal to " + match.otherUsername() + "...");
    }

    // Callback from the network layer when a message arrives. "@Override" because MessageListener declares it.
    @Override
    public void onMessageReceived(Message message) {
        // Messages arrive on the background reader thread. Hop onto the UI thread before touching the window.
        // "() -> handleMessage(message)" is a lambda that will run handleMessage later, on the UI thread.
        SwingUtilities.invokeLater(() -> handleMessage(message));
    }

    // Callback from the network layer when the connection drops.
    @Override
    public void onDisconnected(String reason) {
        // Again switch to the UI thread before updating the window.
        SwingUtilities.invokeLater(() -> {
            // Record that we are no longer connected.
            connected = false;
            // Update the window's enabled/disabled buttons to the offline state.
            frame.setConnectedState(false);
            // Show why we disconnected.
            frame.setStatus("Disconnected: " + reason);
        });
    }

    // Looks at the type of an incoming message and routes it to the matching handler. Runs on the UI thread.
    private void handleMessage(Message message) {
        // A pattern-matching switch: each "case Type name ->" runs when the message is that type.
        switch (message) {
            case RegisterResponse response -> handleRegisterResponse(response);
            case AlbumSyncResponse response -> handleAlbumSyncResponse(response);
            case MatchesResponse response -> handleMatchesResponse(response);
            // For an incoming proposal we dig out the inner TradeProposal with ".proposal()".
            case IncomingTradeProposal proposal -> handleIncomingProposal(proposal.proposal());
            case TradeAppliedEvent event -> handleTradeApplied(event);
            case InfoMessage infoMessage -> handleInfoMessage(infoMessage);
            // "default" catches anything we did not expect.
            default -> frame.setStatus("Received unsupported message from the server.");
        }
    }

    // Handles the server's reply to our registration attempt.
    private void handleRegisterResponse(RegisterResponse response) {
        // If registration failed, show the reason and stop here.
        if (!response.success()) {
            frame.showError(response.message());
            frame.setStatus(response.message());
            return;
        }

        // Registration succeeded: mark ourselves connected.
        connected = true;
        // Store and display the starter album the server gave us.
        currentAlbum = response.albumState();
        frame.setConnectedState(true);
        frame.showAlbum(currentAlbum);
        frame.setStatus(response.message());
        // Immediately ask for trade matches so the user sees opportunities right away.
        refreshMatches();
    }

    // Handles the server's reply after we saved an album.
    private void handleAlbumSyncResponse(AlbumSyncResponse response) {
        // If the server returned an album, adopt it and redraw.
        if (response.albumState() != null) {
            currentAlbum = response.albumState();
            frame.showAlbum(currentAlbum);
        }
        // Always update the status line.
        frame.setStatus(response.message());
        // Show an error popup if the save failed.
        if (!response.success()) {
            frame.showError(response.message());
        }
        // If it succeeded, refresh matches because our album (and thus possible trades) may have changed.
        if (response.success()) {
            refreshMatches();
        }
    }

    // Handles a list of trade matches from the server.
    private void handleMatchesResponse(MatchesResponse response) {
        // Show the matches in the window's dropdown.
        frame.showMatches(response.matches());
        // Report how many were found. ".size()" is the count of items in the list.
        frame.setStatus("Found " + response.matches().size() + " possible trade partner(s).");
    }

    // Handles a trade proposal that someone else sent to us.
    private void handleIncomingProposal(TradeProposal proposal) {
        // Ask the window to show the proposal and collect the user's answer (accept/decline + any chosen stickers).
        StickerExchangeFrame.ProposalResponse answer = frame.answerTradeProposal(proposal);
        // Send our decision back to the server. The status text uses a ternary: a different message depending on
        // whether we accepted ("accept ? ... : ...").
        send(new TradeDecisionRequest(proposal.proposalId(), answer.accept(), answer.selectedOffer()),
                answer.accept()
                        ? "Sending trade decision..."
                        : "Declining trade from " + proposal.requesterUsername() + "...");
    }

    // Handles the server telling us a trade was completed and our album changed.
    private void handleTradeApplied(TradeAppliedEvent event) {
        // Adopt the new album and redraw it.
        currentAlbum = event.albumState();
        frame.showAlbum(currentAlbum);
        // Update the status line and pop up an info dialog with the result.
        frame.setStatus(event.message());
        frame.showInfo(event.message());
        // Refresh matches since our collection changed.
        refreshMatches();
    }

    // Handles a general informational message from the server.
    private void handleInfoMessage(InfoMessage infoMessage) {
        // Some info messages include a refreshed album; adopt it if present.
        if (infoMessage.albumState() != null) {
            currentAlbum = infoMessage.albumState();
            frame.showAlbum(currentAlbum);
        }
        // Always update the status line.
        frame.setStatus(infoMessage.message());
        // If the message indicates a failure, show it as an error popup too.
        if (!infoMessage.success()) {
            frame.showError(infoMessage.message());
        }
    }

    // Shared private helper that sends a message and updates the status bar, handling send failures uniformly.
    private void send(Message message, String statusMessage) {
        try {
            // Try to send the message through the network connection.
            connection.send(message);
            // On success, show the provided status text.
            frame.setStatus(statusMessage);
        } catch (IOException exception) {
            // Sending failed (connection broke). Mark offline, switch the UI to offline mode, and inform the user.
            connected = false;
            frame.setConnectedState(false);
            frame.showError("Connection to the server failed: " + exception.getMessage());
            frame.setStatus("Disconnected.");
        }
    }
}
