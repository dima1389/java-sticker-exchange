package com.stickerexchange.server.app;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public final class ServerMain {
    private static final int DEFAULT_PORT = 5050;

    private ServerMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Sticker exchange server listening on port " + port + ".");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                clientSocket.close();
            }
        }
    }
}
