// The "client.ui" package holds the VIEW: everything the user sees and clicks.
package com.stickerexchange.client.ui;

// The controller this window reports user actions to.
import com.stickerexchange.client.controller.ClientController;
// Shared model types displayed in the window.
import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import com.stickerexchange.common.model.TradeProposal;
// "java.awt" classes describe layout and basic windowing (positions, sizes, arrangement).
import java.awt.BorderLayout;   // arranges items in North/South/East/West/Center regions
import java.awt.Dimension;      // a width+height pair
import java.awt.FlowLayout;     // lays items left-to-right in a row
import java.awt.Component;      // the common parent type of all UI widgets
import java.awt.GridLayout;     // arranges items in a grid of rows and columns
import java.util.List;          // ordered collection
import java.awt.Window;         // a top-level window (used to anchor dialogs)
import java.util.LinkedHashMap; // a map that remembers insertion order
import java.util.SortedSet;     // sorted, duplicate-free collection
import java.util.Map;           // key -> value lookup
import java.util.TreeSet;       // concrete sorted set
import java.util.stream.Collectors; // stream collecting recipes
import java.util.Set;           // duplicate-free collection
import java.util.concurrent.atomic.AtomicReference; // a holder we can set from inside a lambda
// "javax.swing" classes are the actual on-screen widgets (buttons, text fields, etc.).
import javax.swing.DefaultComboBoxModel; // the data behind a dropdown
import javax.swing.JButton;     // a clickable button
import javax.swing.JComboBox;   // a dropdown list
import javax.swing.JCheckBox;   // a tick box
import javax.swing.JFrame;      // a main application window
import javax.swing.JDialog;     // a pop-up window
import javax.swing.JLabel;      // a small piece of non-editable text
import javax.swing.JOptionPane; // ready-made message/confirm dialogs
import javax.swing.JPanel;      // a container that groups other widgets
import javax.swing.JScrollPane; // adds scroll bars around a widget
import javax.swing.JSplitPane;  // splits an area into two resizable halves
import javax.swing.JTextArea;   // a multi-line text box
import javax.swing.JTextField;  // a single-line text box
import javax.swing.SwingUtilities; // helpers for the UI thread
import javax.swing.border.EmptyBorder; // adds empty padding around a panel

import javax.swing.border.TitledBorder; // a border with a caption
/**
 * StickerExchangeFrame — the MAIN WINDOW (the VIEW in MVC). It builds every on-screen control, shows
 * the user's album and possible trades, and forwards button clicks to the {@link ClientController}.
 *
 * <p>KEY CONCEPT — inheritance: {@code extends JFrame} means this class IS a window. It inherits all of
 * JFrame's abilities (title bar, close button, sizing) and adds our own sticker-trading widgets on top.
 *
 * <p>KEY CONCEPT — event-driven UI: nothing happens until the user acts. We attach "action listeners"
 * (small pieces of code) to buttons; Swing runs them when the button is clicked.
 *
 * <p>KEY CONCEPT — composition: the window is assembled from many smaller widgets and two helper inner
 * classes ({@code StickerGridPanel} for the 1..99 grids and {@code TradeSelectionDialog} for choosing
 * stickers).
 */
// The window class. "final" prevents extension; "extends JFrame" makes it a window.
public final class StickerExchangeFrame extends JFrame {
    // Below are the window's widgets, created up-front as fields so every method can refer to them.
    // A text box for the server address, pre-filled with "localhost", about 12 characters wide.
    private final JTextField hostField = new JTextField("localhost", 12);
    // A text box for the port, pre-filled with "5050".
    private final JTextField portField = new JTextField("5050", 6);
    // A text box for the username (empty to start).
    private final JTextField usernameField = new JTextField(12);
    // The four action buttons.
    private final JButton connectButton = new JButton("Connect");
    private final JButton saveAlbumButton = new JButton("Save album");
    private final JButton refreshMatchesButton = new JButton("Find matches");
    private final JButton proposeTradeButton = new JButton("Propose trade");
    // Two grids of 99 checkboxes: one for duplicate stickers, one for missing stickers.
    private final StickerGridPanel duplicatesPanel = new StickerGridPanel("Duplicate stickers");
    private final StickerGridPanel missingPanel = new StickerGridPanel("Missing stickers");
    // A dropdown listing the trade matches the server found. The "<TradeMatch>" means it holds TradeMatch items.
    private final JComboBox<TradeMatch> matchesComboBox = new JComboBox<>();
    // A multi-line text area showing details about the selected match.
    private final JTextArea matchDetailsArea = new JTextArea();
    // A label at the bottom acting as a status bar.
    private final JLabel statusLabel = new JLabel("Disconnected.");

    // A reference to the controller (set later via bind). Not final because it is assigned after construction.
    private ClientController controller;

    // The constructor builds and arranges the whole window.
    public StickerExchangeFrame() {
        // "super(...)" calls the JFrame constructor to set the window's title.
        super("Sticker Exchange");
        // Make clicking the window's X button exit the whole program.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Refuse to shrink below this width/height.
        setMinimumSize(new Dimension(1200, 760));
        // Use a BorderLayout (North/South/Center regions) with 8px gaps between regions.
        setLayout(new BorderLayout(8, 8));
        // Add 8px of empty padding around the window's content. The cast "(JPanel)" tells Java the content pane
        // is a JPanel so we can call setBorder on it.
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        // Wire the two grids so ticking a sticker in one un-ticks it in the other (a sticker can't be both
        // duplicate and missing). Each panel is told about the other.
        duplicatesPanel.addMutualExclusion(missingPanel);
        missingPanel.addMutualExclusion(duplicatesPanel);

        // Place the three main areas: connection controls on top, content in the middle, status at the bottom.
        add(buildConnectionPanel(), BorderLayout.NORTH);
        add(buildMainContent(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        // Configure the match-details text area: read-only and wrapping lines nicely at word boundaries.
        matchDetailsArea.setEditable(false);
        matchDetailsArea.setLineWrap(true);
        matchDetailsArea.setWrapStyleWord(true);
        // When the user picks a different match in the dropdown, refresh the details panel. The lambda runs on
        // each selection change.
        matchesComboBox.addActionListener(event -> updateMatchDetails());
        // Start in the disconnected state (most buttons disabled).
        setConnectedState(false);
    }

    // Connects this window to a controller and attaches click handlers to the buttons.
    public void bind(ClientController clientController) {
        // Store the controller so other methods can use it.
        this.controller = clientController;
        // When "Connect" is clicked, read the host/port/username fields and ask the controller to connect.
        connectButton.addActionListener(event -> controller.connect(
                hostField.getText().trim(),
                readPort(),
                usernameField.getText().trim()));
        // When "Save album" is clicked, read the album from the checkboxes and ask the controller to save it.
        saveAlbumButton.addActionListener(event -> controller.saveAlbum(readAlbumFromUi()));
        // When "Find matches" is clicked, ask the controller to refresh matches.
        refreshMatchesButton.addActionListener(event -> controller.refreshMatches());
        // When "Propose trade" is clicked, pass the currently selected match to the controller.
        proposeTradeButton.addActionListener(event -> controller.proposeTrade(getSelectedMatch()));
    }

    // Enables or disables widgets depending on whether we are connected. Before connecting, you can edit the
    // host/port/username; after connecting, you can edit your album and trade.
    public void setConnectedState(boolean connected) {
        // The connection fields and Connect button are only usable while OFFLINE, so enable them when NOT connected.
        // "!connected" means "the opposite of connected".
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        usernameField.setEnabled(!connected);
        connectButton.setEnabled(!connected);
        // The album/trade actions are only usable while ONLINE.
        saveAlbumButton.setEnabled(connected);
        refreshMatchesButton.setEnabled(connected);
        // Propose trade additionally requires at least one match in the dropdown. "&&" requires both conditions.
        proposeTradeButton.setEnabled(connected && matchesComboBox.getItemCount() > 0);
        // Enable or disable all the checkboxes in both grids.
        duplicatesPanel.setPanelEnabled(connected);
        missingPanel.setPanelEnabled(connected);
    }

    // Redraws the two sticker grids to reflect a given album.
    public void showAlbum(AlbumState albumState) {
        // Tick the duplicate checkboxes that match the album's duplicates.
        duplicatesPanel.setSelectedStickers(albumState.duplicates());
        // Tick the missing checkboxes that match the album's missing stickers.
        missingPanel.setSelectedStickers(albumState.missing());
    }

    // Reads the current checkbox selections back into an AlbumState object.
    public AlbumState readAlbumFromUi() {
        // "AlbumState.of(...)" builds an album from the two sets of selected stickers.
        return AlbumState.of(duplicatesPanel.getSelectedStickers(), missingPanel.getSelectedStickers());
    }

    // Fills the matches dropdown with the list of matches received from the server.
    public void showMatches(List<TradeMatch> matches) {
        // A combo box needs a "model" holding its items; we build a fresh one.
        DefaultComboBoxModel<TradeMatch> model = new DefaultComboBoxModel<>();
        // Add each match to the model. A for-each loop visits every match in turn.
        for (TradeMatch match : matches) {
            model.addElement(match);
        }
        // Replace the dropdown's contents with the new model.
        matchesComboBox.setModel(model);
        // Enable "Propose trade" only if we have a controller and at least one match.
        proposeTradeButton.setEnabled(controller != null && matchesComboBox.getItemCount() > 0);
        // Refresh the details text for whatever is now selected.
        updateMatchDetails();
    }

    // Returns the match currently selected in the dropdown (or null if none).
    public TradeMatch getSelectedMatch() {
        // "getSelectedItem()" returns a generic Object, so we cast it to TradeMatch.
        return (TradeMatch) matchesComboBox.getSelectedItem();
    }

    // Pops up a dialog asking the user to pick exactly the right number of stickers to OFFER in a trade.
    public SortedSet<Integer> selectOutgoingStickers(TradeMatch match) {
        // Build the instruction text shown at the top of the dialog.
        String message = "You have more possible stickers than " + match.otherUsername()
                + ". Select exactly " + match.tradeSize() + " stickers to offer.\n\nAvailable: "
                + formatStickers(match.offerFromCurrentUser());
        // Show the selection dialog and return whatever the user chose (or null if they cancel).
        return TradeSelectionDialog.selectStickers(
                this,
                "Select outgoing stickers",
                message,
                match.offerFromCurrentUser(),
                match.tradeSize());
    }

    // Shows an incoming trade proposal and collects the user's answer (accept/decline plus any chosen stickers).
    public ProposalResponse answerTradeProposal(TradeProposal proposal) {
        // Build the descriptive text for the proposal.
        String proposalText = buildProposalText(proposal);
        // Case 1: the recipient (this user) must CHOOSE which stickers to return.
        if (proposal.recipientSelectionRequired()) {
            // Show the selection dialog listing the candidate stickers.
            SortedSet<Integer> selected = TradeSelectionDialog.selectStickers(
                    this,
                    "Incoming trade from " + proposal.requesterUsername(),
                    proposalText + "\n\nSelect exactly " + proposal.expectedRecipientSelectionSize() + " stickers to return.",
                    proposal.recipientOfferCandidates(),
                    proposal.expectedRecipientSelectionSize());
            // If they cancelled (null), treat it as a decline with no stickers.
            if (selected == null) {
                return new ProposalResponse(false, new TreeSet<>());
            }
            // Otherwise it is an acceptance carrying their chosen stickers.
            return new ProposalResponse(true, selected);
        }

        // Case 2: a simple yes/no proposal. Show a confirm dialog with Yes/No buttons.
        int decision = JOptionPane.showConfirmDialog(
                this,
                proposalText,
                "Incoming trade from " + proposal.requesterUsername(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        // Accept only if they pressed Yes; no sticker choice is needed here, so pass an empty set.
        return new ProposalResponse(decision == JOptionPane.YES_OPTION, new TreeSet<>());
    }

    // Updates the status bar text at the bottom of the window.
    public void setStatus(String message) {
        statusLabel.setText(message);
    }

    // Shows a red error popup. "this" anchors the dialog to this window.
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Shows a neutral information popup.
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    // Makes the window appear on screen. Called once at startup.
    public void showWindow() {
        // Building/showing UI must happen on the UI thread, so schedule it with invokeLater.
        SwingUtilities.invokeLater(() -> {
            // "pack()" sizes the window to fit its contents.
            pack();
            // Center the window on screen ("null" means relative to the whole screen).
            setLocationRelativeTo(null);
            // Finally make it visible.
            setVisible(true);
        });
    }

    // Builds the top strip of controls (host, port, username, and the four buttons). Returns the panel.
    private JPanel buildConnectionPanel() {
        // A panel laid out left-to-right with 8px horizontal gaps.
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        // Add a caption label, then the matching field, alternating across the row.
        panel.add(new JLabel("Host"));
        panel.add(hostField);
        panel.add(new JLabel("Port"));
        panel.add(portField);
        panel.add(new JLabel("Username"));
        panel.add(usernameField);
        // Then the four action buttons.
        panel.add(connectButton);
        panel.add(saveAlbumButton);
        panel.add(refreshMatchesButton);
        panel.add(proposeTradeButton);
        // Hand the assembled panel back to the caller.
        return panel;
    }

    // Builds the central area: the two album grids on the left, match info on the right, in a split pane.
    private JSplitPane buildMainContent() {
        // A panel holding the two grids side by side (1 row, 2 columns) with 8px gaps.
        JPanel albumsPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        // Wrap each grid in a scroll pane so it can scroll if it gets large.
        albumsPanel.add(new JScrollPane(duplicatesPanel));
        albumsPanel.add(new JScrollPane(missingPanel));

        // A panel for the matches dropdown (top) and its details text (center).
        JPanel matchesPanel = new JPanel(new BorderLayout(8, 8));
        // Add a little left padding.
        matchesPanel.setBorder(new EmptyBorder(0, 8, 0, 0));
        matchesPanel.add(matchesComboBox, BorderLayout.NORTH);
        matchesPanel.add(new JScrollPane(matchDetailsArea), BorderLayout.CENTER);

        // A split pane lets the user drag a divider between the albums (left) and matches (right).
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, albumsPanel, matchesPanel);
        // Give the left side 70% of the extra space when the window resizes.
        splitPane.setResizeWeight(0.7);
        return splitPane;
    }

    // Rebuilds the right-hand details text describing the currently selected match.
    private void updateMatchDetails() {
        // Get the selected match (may be null if the list is empty).
        TradeMatch selectedMatch = getSelectedMatch();
        // If nothing is selected, show a placeholder and disable the propose button.
        if (selectedMatch == null) {
            matchDetailsArea.setText("No matching exchanges found.");
            proposeTradeButton.setEnabled(false);
            return;
        }

        // A StringBuilder efficiently assembles text piece by piece. ".append(...)" adds to the end and returns
        // the same builder, so calls can be chained.
        StringBuilder builder = new StringBuilder();
        // "\n\n" inserts two line breaks (a blank line) for readability.
        builder.append("Trade with ").append(selectedMatch.otherUsername()).append("\n\n");
        builder.append("You can offer: ").append(formatStickers(selectedMatch.offerFromCurrentUser())).append("\n\n");
        builder.append("You can receive: ").append(formatStickers(selectedMatch.offerFromOtherUser())).append("\n\n");
        // Add a tailored note depending on which side (if any) must choose stickers.
        if (selectedMatch.currentUserMustSelect()) {
            builder.append("You must select ").append(selectedMatch.tradeSize()).append(" stickers before sending the proposal.");
        } else if (selectedMatch.otherUserMustSelect()) {
            builder.append(selectedMatch.otherUsername())
                    .append(" must select ")
                    .append(selectedMatch.tradeSize())
                    .append(" stickers when answering the proposal.");
        } else {
            builder.append("Both sides can exchange all matching stickers immediately.");
        }
        // Push the finished text into the details area. ".toString()" turns the builder into a plain String.
        matchDetailsArea.setText(builder.toString());
        // Enable the propose button as long as a controller is wired up.
        proposeTradeButton.setEnabled(controller != null);
    }

    // Builds the human-readable description shown when an incoming proposal arrives.
    private String buildProposalText(TradeProposal proposal) {
        // Assemble the text with a StringBuilder.
        StringBuilder builder = new StringBuilder();
        builder.append(proposal.requesterUsername()).append(" wants to trade with you.\n\n");
        builder.append("They offer: ").append(formatStickers(proposal.requesterOffer())).append("\n\n");
        // Describe what the user gives back, depending on whether they must choose.
        if (proposal.recipientSelectionRequired()) {
            builder.append("You may return any ")
                    .append(proposal.expectedRecipientSelectionSize())
                    .append(" stickers from: ")
                    .append(formatStickers(proposal.recipientOfferCandidates()));
        } else {
            builder.append("You would return: ").append(formatStickers(proposal.recipientOfferCandidates()));
        }
        // Return the assembled message text.
        return builder.toString();
    }

    // Reads the port text field and converts it to a number. Returns -1 if the text is not a valid number.
    private int readPort() {
        try {
            // "parseInt" throws if the text is not a whole number, which we catch below.
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException exception) {
            // The text was not a number; warn the user and signal failure with -1.
            showError("Port must be a valid number.");
            return -1;
        }
    }

    // Turns a set of sticker numbers into a friendly comma-separated string, e.g. "3, 7, 42".
    private String formatStickers(SortedSet<Integer> stickers) {
        // Stream pipeline: ".map(String::valueOf)" converts each number to text, then
        // ".collect(Collectors.joining(", "))" glues them together separated by commas.
        return stickers.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    // Reads which checkboxes are ticked in a map and returns their sticker numbers as a sorted set.
    private static SortedSet<Integer> collectSelected(Map<Integer, JCheckBox> checkBoxes) {
        // Start with an empty sorted set.
        SortedSet<Integer> selected = new TreeSet<>();
        // Visit every (stickerNumber, checkbox) pair.
        for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
            // If this checkbox is ticked, record its sticker number.
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        // Return the set of ticked numbers.
        return selected;
    }

    // A tiny record bundling the user's answer to a proposal: whether they accepted and which stickers (if any)
    // they chose to give back. Used as the return value of answerTradeProposal.
    public record ProposalResponse(boolean accept, SortedSet<Integer> selectedOffer) {
    }

    // A reusable grid of 99 checkboxes (one per sticker number). Used twice: once for duplicates, once for
    // missing. "extends JPanel" makes it a panel; "static" means it does not need an outer-frame instance.
    public static final class StickerGridPanel extends JPanel {
        // A map from sticker number to its checkbox. LinkedHashMap keeps them in the order 1..99.
        private final Map<Integer, JCheckBox> checkBoxes = new LinkedHashMap<>();

        // Constructor: builds the grid and its 99 checkboxes under a titled border.
        public StickerGridPanel(String title) {
            // "GridLayout(0, 6, ...)" arranges children in 6 columns and as many rows as needed.
            setLayout(new GridLayout(0, 6, 8, 4));
            // Draw a captioned border around the grid.
            setBorder(new TitledBorder(title));
            // Create one checkbox for each sticker number 1..99. (This loop necessarily creates 99 widgets.)
            for (int sticker = 1; sticker <= 99; sticker++) {
                // The checkbox label is the sticker number as text.
                JCheckBox checkBox = new JCheckBox(String.valueOf(sticker));
                // Remember the checkbox so we can read/modify it later.
                checkBoxes.put(sticker, checkBox);
                // Add the checkbox to the visible grid.
                add(checkBox);
            }
        }

        // Wires this panel so that ticking a sticker here automatically UN-ticks the same sticker in another
        // panel (used to keep "duplicates" and "missing" mutually exclusive).
        public void addMutualExclusion(StickerGridPanel otherPanel) {
            // For each checkbox in this panel...
            for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
                int sticker = entry.getKey();
                JCheckBox checkBox = entry.getValue();
                // ...attach a listener: whenever it becomes ticked, clear the matching box in the other panel.
                checkBox.addActionListener(event -> {
                    if (checkBox.isSelected()) {
                        otherPanel.setStickerSelected(sticker, false);
                    }
                });
            }
        }

        // Returns the set of currently ticked sticker numbers.
        public SortedSet<Integer> getSelectedStickers() {
            return collectSelected(checkBoxes);
        }

        // Ticks exactly the stickers in the given set and unticks the rest.
        public void setSelectedStickers(Set<Integer> selectedStickers) {
            // For each checkbox, set its ticked state to whether its number is in the given set.
            for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
                entry.getValue().setSelected(selectedStickers.contains(entry.getKey()));
            }
        }

        // Ticks or unticks a single sticker by number, if it exists.
        public void setStickerSelected(int sticker, boolean selected) {
            // Look up the checkbox for that number.
            JCheckBox checkBox = checkBoxes.get(sticker);
            // Only change it if it was found (not null).
            if (checkBox != null) {
                checkBox.setSelected(selected);
            }
        }

        // Enables or disables every checkbox at once (used when going online/offline).
        public void setPanelEnabled(boolean enabled) {
            for (JCheckBox checkBox : checkBoxes.values()) {
                checkBox.setEnabled(enabled);
            }
        }
    }

    // A helper that shows a modal pop-up letting the user pick EXACTLY a required number of stickers from a set
    // of candidates. "static" = it does not need an outer frame; it is a self-contained dialog builder.
    public static final class TradeSelectionDialog {
        // Private constructor: this class only offers a static method and is never instantiated.
        private TradeSelectionDialog() {
        }

        // Shows the dialog and returns the chosen stickers, or null if the user cancelled.
        public static SortedSet<Integer> selectStickers(
                Component parent,
                String title,
                String message,
                SortedSet<Integer> candidates,
                int requiredCount) {
            // Find the window that owns "parent" so the dialog appears attached to it (ternary handles null parent).
            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            // Create a MODAL dialog: "modal" means it blocks interaction with the main window until answered.
            JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
            // Arrange the dialog's contents in North/Center/South regions.
            dialog.setLayout(new BorderLayout(8, 8));

            // A read-only, word-wrapping, transparent text area at the top showing the instructions.
            JTextArea messageArea = new JTextArea(message);
            messageArea.setEditable(false);
            messageArea.setWrapStyleWord(true);
            messageArea.setLineWrap(true);
            messageArea.setOpaque(false);
            dialog.add(messageArea, BorderLayout.NORTH);

            // The center holds a grid (5 columns) of candidate checkboxes.
            JPanel candidatePanel = new JPanel(new GridLayout(0, 5, 8, 4));
            // Map each candidate number to its checkbox, in order.
            Map<Integer, JCheckBox> checkBoxes = new LinkedHashMap<>();
            // Build one checkbox per candidate sticker.
            for (Integer candidate : candidates) {
                JCheckBox checkBox = new JCheckBox(String.valueOf(candidate));
                checkBoxes.put(candidate, checkBox);
                candidatePanel.add(checkBox);
            }
            // Add the grid inside a scroll pane (in case there are many candidates).
            dialog.add(new JScrollPane(candidatePanel), BorderLayout.CENTER);

            // A label tracking how many are selected vs. how many are required.
            JLabel countLabel = new JLabel("Selected 0 of " + requiredCount + ".");
            // The Confirm button; only enabled once the right number is selected. If 0 are required it starts enabled.
            JButton confirmButton = new JButton("Confirm");
            confirmButton.setEnabled(requiredCount == 0);
            // The Cancel button.
            JButton cancelButton = new JButton("Cancel");

            // An AtomicReference is a holder we can write to from inside a lambda (ordinary local variables cannot
            // be reassigned by a lambda). It will hold the final result.
            AtomicReference<SortedSet<Integer>> result = new AtomicReference<>();
            // A reusable piece of code (a Runnable) that recounts selections and updates the label/button. It is
            // run every time a checkbox changes.
            Runnable updateSelectionState = () -> {
                // Count how many checkboxes are currently ticked.
                int selectedCount = 0;
                for (JCheckBox checkBox : checkBoxes.values()) {
                    if (checkBox.isSelected()) {
                        selectedCount++;
                    }
                }
                // Update the "Selected X of Y" label.
                countLabel.setText("Selected " + selectedCount + " of " + requiredCount + ".");
                // Allow confirming only when exactly the required number are selected.
                confirmButton.setEnabled(selectedCount == requiredCount);
            };
            // Attach the recount logic to every candidate checkbox. ".run()" executes the Runnable above.
            for (JCheckBox checkBox : checkBoxes.values()) {
                checkBox.addActionListener(event -> updateSelectionState.run());
            }

            // When Confirm is clicked, store the selected stickers into the result holder and close the dialog.
            confirmButton.addActionListener(event -> {
                result.set(collectSelected(checkBoxes));
                dialog.dispose();
            });
            // When Cancel is clicked, simply close the dialog, leaving the result holder empty (null).
            cancelButton.addActionListener(event -> dialog.dispose());

            // Build the footer: the count label on the left, the buttons on the right.
            JPanel footerPanel = new JPanel(new BorderLayout());
            footerPanel.add(countLabel, BorderLayout.WEST);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(confirmButton);
            buttonPanel.add(cancelButton);
            footerPanel.add(buttonPanel, BorderLayout.EAST);

            // Add the footer at the bottom and finalize size/position.
            dialog.add(footerPanel, BorderLayout.SOUTH);
            dialog.setSize(480, 420);
            dialog.setLocationRelativeTo(parent);
            // "setVisible(true)" on a MODAL dialog BLOCKS here until the dialog is closed (Confirm or Cancel).
            dialog.setVisible(true);
            // After the dialog closes, return whatever was stored: the chosen stickers, or null if cancelled.
            return result.get();
        }
    }
}
