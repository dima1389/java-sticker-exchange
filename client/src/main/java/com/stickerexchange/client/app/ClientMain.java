package com.stickerexchange.client.app;

import com.stickerexchange.client.ui.StickerExchangeFrame;
import javax.swing.SwingUtilities;

public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StickerExchangeFrame().showWindow());
    }
}
