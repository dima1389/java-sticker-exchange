package com.stickerexchange.client.ui;

import com.stickerexchange.client.controller.ClientController;
import com.stickerexchange.common.model.AlbumState;
import com.stickerexchange.common.model.TradeMatch;
import com.stickerexchange.common.model.TradeProposal;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.SortedSet;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import javax.swing.border.TitledBorder;
public final class StickerExchangeFrame extends JFrame {
    private final JTextField hostField = new JTextField("localhost", 12);
    private final JTextField portField = new JTextField("5050", 6);
    private final JTextField usernameField = new JTextField(12);
    private final JButton connectButton = new JButton("Connect");
    private final JButton saveAlbumButton = new JButton("Save album");
    private final JButton refreshMatchesButton = new JButton("Find matches");
    private final JButton proposeTradeButton = new JButton("Propose trade");
    private final StickerGridPanel duplicatesPanel = new StickerGridPanel("Duplicate stickers");
    private final StickerGridPanel missingPanel = new StickerGridPanel("Missing stickers");
    private final JComboBox<TradeMatch> matchesComboBox = new JComboBox<>();
    private final JTextArea matchDetailsArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Disconnected.");

    private ClientController controller;

    public StickerExchangeFrame() {
        super("Sticker Exchange");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 760));
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        duplicatesPanel.addMutualExclusion(missingPanel);
        missingPanel.addMutualExclusion(duplicatesPanel);

        add(buildConnectionPanel(), BorderLayout.NORTH);
        add(buildMainContent(), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        matchDetailsArea.setEditable(false);
        matchDetailsArea.setLineWrap(true);
        matchDetailsArea.setWrapStyleWord(true);
        matchesComboBox.addActionListener(event -> updateMatchDetails());
        setConnectedState(false);
    }

    public void bind(ClientController clientController) {
        this.controller = clientController;
        connectButton.addActionListener(event -> controller.connect(
                hostField.getText().trim(),
                readPort(),
                usernameField.getText().trim()));
        saveAlbumButton.addActionListener(event -> controller.saveAlbum(readAlbumFromUi()));
        refreshMatchesButton.addActionListener(event -> controller.refreshMatches());
        proposeTradeButton.addActionListener(event -> controller.proposeTrade(getSelectedMatch()));
    }

    public void setConnectedState(boolean connected) {
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        usernameField.setEnabled(!connected);
        connectButton.setEnabled(!connected);
        saveAlbumButton.setEnabled(connected);
        refreshMatchesButton.setEnabled(connected);
        proposeTradeButton.setEnabled(connected && matchesComboBox.getItemCount() > 0);
        duplicatesPanel.setPanelEnabled(connected);
        missingPanel.setPanelEnabled(connected);
    }

    public void showAlbum(AlbumState albumState) {
        duplicatesPanel.setSelectedStickers(albumState.duplicates());
        missingPanel.setSelectedStickers(albumState.missing());
    }

    public AlbumState readAlbumFromUi() {
        return AlbumState.of(duplicatesPanel.getSelectedStickers(), missingPanel.getSelectedStickers());
    }

    public void showMatches(List<TradeMatch> matches) {
        DefaultComboBoxModel<TradeMatch> model = new DefaultComboBoxModel<>();
        for (TradeMatch match : matches) {
            model.addElement(match);
        }
        matchesComboBox.setModel(model);
        proposeTradeButton.setEnabled(controller != null && matchesComboBox.getItemCount() > 0);
        updateMatchDetails();
    }

    public TradeMatch getSelectedMatch() {
        return (TradeMatch) matchesComboBox.getSelectedItem();
    }

    public SortedSet<Integer> selectOutgoingStickers(TradeMatch match) {
        String message = "You have more possible stickers than " + match.otherUsername()
                + ". Select exactly " + match.tradeSize() + " stickers to offer.\n\nAvailable: "
                + formatStickers(match.offerFromCurrentUser());
        return TradeSelectionDialog.selectStickers(
                this,
                "Select outgoing stickers",
                message,
                match.offerFromCurrentUser(),
                match.tradeSize());
    }

    public ProposalResponse answerTradeProposal(TradeProposal proposal) {
        String proposalText = buildProposalText(proposal);
        if (proposal.recipientSelectionRequired()) {
            SortedSet<Integer> selected = TradeSelectionDialog.selectStickers(
                    this,
                    "Incoming trade from " + proposal.requesterUsername(),
                    proposalText + "\n\nSelect exactly " + proposal.expectedRecipientSelectionSize() + " stickers to return.",
                    proposal.recipientOfferCandidates(),
                    proposal.expectedRecipientSelectionSize());
            if (selected == null) {
                return new ProposalResponse(false, new TreeSet<>());
            }
            return new ProposalResponse(true, selected);
        }

        int decision = JOptionPane.showConfirmDialog(
                this,
                proposalText,
                "Incoming trade from " + proposal.requesterUsername(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return new ProposalResponse(decision == JOptionPane.YES_OPTION, new TreeSet<>());
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public void showWindow() {
        SwingUtilities.invokeLater(() -> {
            pack();
            setLocationRelativeTo(null);
            setVisible(true);
        });
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.add(new JLabel("Host"));
        panel.add(hostField);
        panel.add(new JLabel("Port"));
        panel.add(portField);
        panel.add(new JLabel("Username"));
        panel.add(usernameField);
        panel.add(connectButton);
        panel.add(saveAlbumButton);
        panel.add(refreshMatchesButton);
        panel.add(proposeTradeButton);
        return panel;
    }

    private JSplitPane buildMainContent() {
        JPanel albumsPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        albumsPanel.add(new JScrollPane(duplicatesPanel));
        albumsPanel.add(new JScrollPane(missingPanel));

        JPanel matchesPanel = new JPanel(new BorderLayout(8, 8));
        matchesPanel.setBorder(new EmptyBorder(0, 8, 0, 0));
        matchesPanel.add(matchesComboBox, BorderLayout.NORTH);
        matchesPanel.add(new JScrollPane(matchDetailsArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, albumsPanel, matchesPanel);
        splitPane.setResizeWeight(0.7);
        return splitPane;
    }

    private void updateMatchDetails() {
        TradeMatch selectedMatch = getSelectedMatch();
        if (selectedMatch == null) {
            matchDetailsArea.setText("No matching exchanges found.");
            proposeTradeButton.setEnabled(false);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Trade with ").append(selectedMatch.otherUsername()).append("\n\n");
        builder.append("You can offer: ").append(formatStickers(selectedMatch.offerFromCurrentUser())).append("\n\n");
        builder.append("You can receive: ").append(formatStickers(selectedMatch.offerFromOtherUser())).append("\n\n");
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
        matchDetailsArea.setText(builder.toString());
        proposeTradeButton.setEnabled(controller != null);
    }

    private String buildProposalText(TradeProposal proposal) {
        StringBuilder builder = new StringBuilder();
        builder.append(proposal.requesterUsername()).append(" wants to trade with you.\n\n");
        builder.append("They offer: ").append(formatStickers(proposal.requesterOffer())).append("\n\n");
        if (proposal.recipientSelectionRequired()) {
            builder.append("You may return any ")
                    .append(proposal.expectedRecipientSelectionSize())
                    .append(" stickers from: ")
                    .append(formatStickers(proposal.recipientOfferCandidates()));
        } else {
            builder.append("You would return: ").append(formatStickers(proposal.recipientOfferCandidates()));
        }
        return builder.toString();
    }

    private int readPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException exception) {
            showError("Port must be a valid number.");
            return -1;
        }
    }

    private String formatStickers(SortedSet<Integer> stickers) {
        return stickers.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static SortedSet<Integer> collectSelected(Map<Integer, JCheckBox> checkBoxes) {
        SortedSet<Integer> selected = new TreeSet<>();
        for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    public record ProposalResponse(boolean accept, SortedSet<Integer> selectedOffer) {
    }

    public static final class StickerGridPanel extends JPanel {
        private final Map<Integer, JCheckBox> checkBoxes = new LinkedHashMap<>();

        public StickerGridPanel(String title) {
            setLayout(new GridLayout(0, 6, 8, 4));
            setBorder(new TitledBorder(title));
            for (int sticker = 1; sticker <= 99; sticker++) {
                JCheckBox checkBox = new JCheckBox(String.valueOf(sticker));
                checkBoxes.put(sticker, checkBox);
                add(checkBox);
            }
        }

        public void addMutualExclusion(StickerGridPanel otherPanel) {
            for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
                int sticker = entry.getKey();
                JCheckBox checkBox = entry.getValue();
                checkBox.addActionListener(event -> {
                    if (checkBox.isSelected()) {
                        otherPanel.setStickerSelected(sticker, false);
                    }
                });
            }
        }

        public SortedSet<Integer> getSelectedStickers() {
            return collectSelected(checkBoxes);
        }

        public void setSelectedStickers(Set<Integer> selectedStickers) {
            for (Map.Entry<Integer, JCheckBox> entry : checkBoxes.entrySet()) {
                entry.getValue().setSelected(selectedStickers.contains(entry.getKey()));
            }
        }

        public void setStickerSelected(int sticker, boolean selected) {
            JCheckBox checkBox = checkBoxes.get(sticker);
            if (checkBox != null) {
                checkBox.setSelected(selected);
            }
        }

        public void setPanelEnabled(boolean enabled) {
            for (JCheckBox checkBox : checkBoxes.values()) {
                checkBox.setEnabled(enabled);
            }
        }
    }

    public static final class TradeSelectionDialog {
        private TradeSelectionDialog() {
        }

        public static SortedSet<Integer> selectStickers(
                Component parent,
                String title,
                String message,
                SortedSet<Integer> candidates,
                int requiredCount) {
            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
            dialog.setLayout(new BorderLayout(8, 8));

            JTextArea messageArea = new JTextArea(message);
            messageArea.setEditable(false);
            messageArea.setWrapStyleWord(true);
            messageArea.setLineWrap(true);
            messageArea.setOpaque(false);
            dialog.add(messageArea, BorderLayout.NORTH);

            JPanel candidatePanel = new JPanel(new GridLayout(0, 5, 8, 4));
            Map<Integer, JCheckBox> checkBoxes = new LinkedHashMap<>();
            for (Integer candidate : candidates) {
                JCheckBox checkBox = new JCheckBox(String.valueOf(candidate));
                checkBoxes.put(candidate, checkBox);
                candidatePanel.add(checkBox);
            }
            dialog.add(new JScrollPane(candidatePanel), BorderLayout.CENTER);

            JLabel countLabel = new JLabel("Selected 0 of " + requiredCount + ".");
            JButton confirmButton = new JButton("Confirm");
            confirmButton.setEnabled(requiredCount == 0);
            JButton cancelButton = new JButton("Cancel");

            AtomicReference<SortedSet<Integer>> result = new AtomicReference<>();
            Runnable updateSelectionState = () -> {
                int selectedCount = 0;
                for (JCheckBox checkBox : checkBoxes.values()) {
                    if (checkBox.isSelected()) {
                        selectedCount++;
                    }
                }
                countLabel.setText("Selected " + selectedCount + " of " + requiredCount + ".");
                confirmButton.setEnabled(selectedCount == requiredCount);
            };
            for (JCheckBox checkBox : checkBoxes.values()) {
                checkBox.addActionListener(event -> updateSelectionState.run());
            }

            confirmButton.addActionListener(event -> {
                result.set(collectSelected(checkBoxes));
                dialog.dispose();
            });
            cancelButton.addActionListener(event -> dialog.dispose());

            JPanel footerPanel = new JPanel(new BorderLayout());
            footerPanel.add(countLabel, BorderLayout.WEST);
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(confirmButton);
            buttonPanel.add(cancelButton);
            footerPanel.add(buttonPanel, BorderLayout.EAST);

            dialog.add(footerPanel, BorderLayout.SOUTH);
            dialog.setSize(480, 420);
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
            return result.get();
        }
    }
}
