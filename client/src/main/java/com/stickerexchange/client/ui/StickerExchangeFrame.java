package com.stickerexchange.client.ui;

import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

public class StickerExchangeFrame extends JFrame {
    public StickerExchangeFrame() {
        setTitle("Sticker Exchange");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        add(new JLabel("Sticker Exchange client", SwingConstants.CENTER));
        setPreferredSize(new Dimension(400, 300));
        pack();
        setLocationRelativeTo(null);
    }

    public void showWindow() {
        setVisible(true);
    }
}
