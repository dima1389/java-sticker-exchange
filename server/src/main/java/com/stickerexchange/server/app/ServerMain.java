package com.stickerexchange.server.app;

import com.stickerexchange.common.protocol.AlbumSyncRequest;
import com.stickerexchange.common.protocol.FindMatchesRequest;
import com.stickerexchange.common.protocol.InfoMessage;
import com.stickerexchange.common.protocol.Message;
import com.stickerexchange.common.protocol.ProposeTradeRequest;
import com.stickerexchange.common.protocol.RegisterRequest;
import com.stickerexchange.common.protocol.RegisterResponse;
import com.stickerexchange.common.protocol.TradeDecisionRequest;
import com.stickerexchange.server.core.ExchangeCoordinator;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerMain {
    private static final int DEFAULT_PORT = 5050;

    private ServerMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        ExchangeCoordinator coordinator = new ExchangeCoordinator();
        ExecutorService executorService = Executors.newCachedThreadPool();

        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Sticker exchange server listening on port " + port + ".");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandler(clientSocket, coordinator));
            }
        }
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        return Integer.parseInt(args[0]);
    }

    private static final class ClientHandler implements Runnable, ExchangeCoordinator.ClientConnection {
        private final Socket socket;
        private final ExchangeCoordinator coordinator;

        private ObjectOutputStream outputStream;
        private ObjectInputStream inputStream;
        private String username;

        private ClientHandler(Socket socket, ExchangeCoordinator coordinator) {
            this.socket = socket;
            this.coordinator = coordinator;
        }

        @Override
        public void run() {
            try (socket) {
                socket.setTcpNoDelay(true);
                outputStream = new ObjectOutputStream(socket.getOutputStream());
                outputStream.flush();
                inputStream = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    Object incomingObject = inputStream.readObject();
                    if (!(incomingObject instanceof Message message)) {
                        send(new InfoMessage(false, "Unsupported payload received.", null));
                        continue;
                    }

                    ExchangeCoordinator.CommandResult result = route(message);
                    dispatch(result);
                }
            } catch (EOFException ignored) {
            } catch (IOException | ClassNotFoundException exception) {
                System.out.println("Client disconnected: " + exception.getMessage());
            } finally {
                dispatch(new ExchangeCoordinator.CommandResult(null, coordinator.disconnect(username)));
            }
        }

        @Override
        public synchronized void send(Message message) {
            if (outputStream == null) {
                return;
            }
            try {
                outputStream.writeObject(message);
                outputStream.flush();
            } catch (IOException exception) {
                System.out.println("Failed to send message: " + exception.getMessage());
            }
        }

        private ExchangeCoordinator.CommandResult route(Message message) {
            return switch (message) {
                case RegisterRequest registerRequest -> handleRegister(registerRequest);
                case AlbumSyncRequest albumSyncRequest -> ensureRegistered(() -> coordinator.synchronizeAlbum(username, albumSyncRequest));
                case FindMatchesRequest findMatchesRequest -> ensureRegistered(() -> coordinator.findMatches(username, findMatchesRequest));
                case ProposeTradeRequest proposeTradeRequest -> ensureRegistered(() -> coordinator.proposeTrade(username, proposeTradeRequest));
                case TradeDecisionRequest tradeDecisionRequest -> ensureRegistered(() -> coordinator.respondToTrade(username, tradeDecisionRequest));
                default -> ExchangeCoordinator.CommandResult.reply(new InfoMessage(false, "Unsupported message type.", null));
            };
        }

        private ExchangeCoordinator.CommandResult handleRegister(RegisterRequest request) {
            if (username != null) {
                return ExchangeCoordinator.CommandResult.reply(new InfoMessage(false, "This client is already registered as " + username + ".", null));
            }

            ExchangeCoordinator.CommandResult result = coordinator.register(request, this);
            if (result.directMessage() instanceof RegisterResponse registerResponse && registerResponse.success()) {
                username = request.username().trim();
            }
            return result;
        }

        private ExchangeCoordinator.CommandResult ensureRegistered(CheckedResultSupplier supplier) {
            if (username == null) {
                return ExchangeCoordinator.CommandResult.reply(new InfoMessage(false, "Register before sending other requests.", null));
            }
            return supplier.get();
        }

        private void dispatch(ExchangeCoordinator.CommandResult result) {
            if (result == null) {
                return;
            }
            if (result.directMessage() != null) {
                send(result.directMessage());
            }
            for (ExchangeCoordinator.DispatchMessage followUp : result.followUpMessages()) {
                followUp.connection().send(followUp.message());
            }
        }

        @FunctionalInterface
        private interface CheckedResultSupplier {
            ExchangeCoordinator.CommandResult get();
        }
    }
}
