package com.stickerexchange.client.app;

import com.stickerexchange.client.controller.ClientController;
import com.stickerexchange.client.ui.StickerExchangeFrame;

public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        StickerExchangeFrame frame = new StickerExchangeFrame();
        ClientController controller = new ClientController(frame);
        frame.bind(controller);
        frame.showWindow();
    }
}
