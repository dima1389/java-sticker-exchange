// The "server.app" package holds the server's entry point and the per-client networking glue.
package com.stickerexchange.server.app;

// Import every message type the server needs to recognise or send. Each name below is one kind of
// network message defined in the shared "protocol" package.
import com.stickerexchange.common.protocol.AlbumSyncRequest;
import com.stickerexchange.common.protocol.FindMatchesRequest;
import com.stickerexchange.common.protocol.InfoMessage;
import com.stickerexchange.common.protocol.Message;
import com.stickerexchange.common.protocol.ProposeTradeRequest;
import com.stickerexchange.common.protocol.RegisterRequest;
import com.stickerexchange.common.protocol.RegisterResponse;
import com.stickerexchange.common.protocol.TradeDecisionRequest;
// The "brain" that holds all users and trade logic; this file just shuttles messages to/from it.
import com.stickerexchange.server.core.ExchangeCoordinator;
// "EOFException" (End Of File) is thrown when a stream ends — here it signals a client hung up.
import java.io.EOFException;
// "IOException" is the general error for input/output problems (network, files, etc.).
import java.io.IOException;
// Streams that can read/write whole Java OBJECTS (not just raw bytes) over the socket.
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
// "ServerSocket" listens for incoming client connections; "Socket" is one established connection.
import java.net.ServerSocket;
import java.net.Socket;
// Tools for running work on background threads so many clients can be served at once.
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ServerMain — the program that STARTS the server and accepts client connections.
 *
 * <p>WHAT IT DOES (big picture):
 * <ol>
 *   <li>Opens a "server socket" on a port and waits for clients to dial in.</li>
 *   <li>For each client that connects, it hands the connection to a {@code ClientHandler} running on
 *       its own background thread, so many users can be online simultaneously.</li>
 *   <li>Each ClientHandler reads messages from its client, asks the shared
 *       {@link ExchangeCoordinator} what to do, and sends back the results.</li>
 * </ol>
 *
 * <p>KEY CONCEPTS: sockets (network plumbing), threads and a thread pool (doing many things at once),
 * object streams (sending whole objects over the wire), and the {@code switch} statement that routes
 * each message type to the right action.
 */
// Application class. "final" = cannot be extended.
public final class ServerMain {
    // The port number the server listens on if none is given on the command line. A port is like a
    // numbered door on the computer that network traffic uses.
    private static final int DEFAULT_PORT = 5050;

    // Private constructor: this class is only an entry point (it has a main method), never an object.
    private ServerMain() {
    }

    // "main" is the special method the Java runtime calls first to start any program. "String[] args" is
    // the list of command-line arguments. "throws IOException" warns that network setup might fail.
    public static void main(String[] args) throws IOException {
        // Work out which port to use (from args, or the default).
        int port = parsePort(args);
        // Create the single coordinator that remembers all users and trades for the whole server run.
        ExchangeCoordinator coordinator = new ExchangeCoordinator();
        // A "thread pool" that creates/reuses background threads on demand. Each connected client runs on one.
        ExecutorService executorService = Executors.newCachedThreadPool();

        // Register a "shutdown hook": a small task Java runs when the program is asked to stop (e.g. Ctrl+C).
        // Here it tells the thread pool to stop all background work cleanly. "::" makes a method reference.
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdownNow));

        // "try-with-resources": opens the ServerSocket and guarantees it is closed automatically at the end,
        // even if an error occurs. The socket starts listening on our chosen port.
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Print a friendly startup message to the server console.
            System.out.println("Sticker exchange server listening on port " + port + ".");
            // Loop forever, accepting one client after another. "while (true)" runs until the program is stopped.
            while (true) {
                // "accept()" BLOCKS (waits) until a client connects, then returns that client's Socket.
                Socket clientSocket = serverSocket.accept();
                // Hand the new connection to a ClientHandler and run it on a background thread from the pool, so
                // the loop can immediately go back to waiting for the next client.
                executorService.submit(new ClientHandler(clientSocket, coordinator));
            }
        }
    }

    // Helper that decides the port: use the first command-line argument if present, otherwise the default.
    private static int parsePort(String[] args) {
        // "args.length" is how many arguments were supplied. Zero means none were given.
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        // "Integer.parseInt" turns the text argument (e.g. "6000") into a number.
        return Integer.parseInt(args[0]);
    }

    // A "nested class": ClientHandler lives inside ServerMain. "static" means it does not need an outer
    // ServerMain object. It manages the conversation with ONE connected client.
    //   - "implements Runnable" means it can be run on a thread (it must provide a run() method).
    //   - "implements ExchangeCoordinator.ClientConnection" means the coordinator can send messages back
    //     to this client through it.
    private static final class ClientHandler implements Runnable, ExchangeCoordinator.ClientConnection {
        // The network connection to this one client. "final" = set once and never reassigned.
        private final Socket socket;
        // A shared reference to the server's brain, so this handler can ask it to do the real work.
        private final ExchangeCoordinator coordinator;

        // Stream used to SEND objects to this client. Set up later inside run().
        private ObjectOutputStream outputStream;
        // Stream used to RECEIVE objects from this client.
        private ObjectInputStream inputStream;
        // Remembers this client's username once they register (null until then).
        private String username;

        // Constructor: store the socket and the shared coordinator for later use.
        private ClientHandler(Socket socket, ExchangeCoordinator coordinator) {
            this.socket = socket;
            this.coordinator = coordinator;
        }

        // "@Override" because Runnable requires a run() method. This is what the background thread executes.
        @Override
        public void run() {
            // try-with-resources on the socket: it is closed automatically when this block ends.
            try (socket) {
                // Disable a network optimisation (Nagle's algorithm) so small messages are sent immediately
                // instead of being buffered, keeping the app responsive.
                socket.setTcpNoDelay(true);
                // Create the OUTPUT stream first and flush its header; the order matters because the matching
                // ObjectInputStream on the other side blocks until it reads this header.
                outputStream = new ObjectOutputStream(socket.getOutputStream());
                outputStream.flush();
                // Now create the INPUT stream to read objects this client sends us.
                inputStream = new ObjectInputStream(socket.getInputStream());

                // Keep reading messages from this client until they disconnect or an error occurs.
                while (true) {
                    // "readObject()" waits for and reads the next object the client sent.
                    Object incomingObject = inputStream.readObject();
                    // "instanceof ... message" checks the object is actually a Message AND, if so, assigns it to a
                    // new variable "message" in one step (this is called "pattern matching"). The "!" negates it.
                    if (!(incomingObject instanceof Message message)) {
                        // The client sent something that is not a valid Message; tell them and keep going.
                        send(new InfoMessage(false, "Unsupported payload received.", null));
                        // "continue" skips the rest of the loop body and waits for the next message.
                        continue;
                    }

                    // Decide what this message means and let the coordinator compute the outcome.
                    ExchangeCoordinator.CommandResult result = route(message);
                    // Send any replies/notifications the coordinator produced.
                    dispatch(result);
                }
            } catch (EOFException ignored) {
                // EOFException simply means the client closed the connection. This is normal, so we deliberately
                // do nothing (the variable is even named "ignored" to signal that on purpose).
            } catch (IOException | ClassNotFoundException exception) {
                // Any other network problem or unknown class: log it. The "|" lets one catch handle two error types.
                System.out.println("Client disconnected: " + exception.getMessage());
            } finally {
                // "finally" ALWAYS runs, whether or not there was an error. We tell the coordinator this user left
                // (so pending trades get cleaned up) and deliver any resulting notifications to other users.
                dispatch(new ExchangeCoordinator.CommandResult(null, coordinator.disconnect(username)));
            }
        }

        // The method the coordinator calls to send a message back to THIS client. "synchronized" prevents two
        // threads from writing to the same stream at the same time and garbling the data.
        @Override
        public synchronized void send(Message message) {
            // If the output stream was never set up (very early failure), there is nothing to send to.
            if (outputStream == null) {
                return;
            }
            // Writing to the network can fail, so guard it with try/catch.
            try {
                // Serialize and send the object, then flush to push it out immediately.
                outputStream.writeObject(message);
                outputStream.flush();
            } catch (IOException exception) {
                // If sending fails, log it; the read loop will soon notice the broken connection and end.
                System.out.println("Failed to send message: " + exception.getMessage());
            }
        }

        // Looks at the message type and calls the matching coordinator method. Returns a CommandResult that
        // describes what to send back.
        private ExchangeCoordinator.CommandResult route(Message message) {
            // A "switch expression" picks a branch based on the actual TYPE of "message" (pattern matching).
            // Each "case X x ->" runs when the message is an X, giving it the name x. The whole switch produces
            // a value that we return.
            return switch (message) {
                // A registration attempt is handled specially (see handleRegister below).
                case RegisterRequest registerRequest -> handleRegister(registerRequest);
                // For all other requests, "ensureRegistered" first checks the user has registered, then runs the
                // given action. The "() -> ..." is a lambda: a piece of code packaged to run later.
                case AlbumSyncRequest albumSyncRequest -> ensureRegistered(() -> coordinator.synchronizeAlbum(username, albumSyncRequest));
                case FindMatchesRequest findMatchesRequest -> ensureRegistered(() -> coordinator.findMatches(username, findMatchesRequest));
                case ProposeTradeRequest proposeTradeRequest -> ensureRegistered(() -> coordinator.proposeTrade(username, proposeTradeRequest));
                case TradeDecisionRequest tradeDecisionRequest -> ensureRegistered(() -> coordinator.respondToTrade(username, tradeDecisionRequest));
                // "default" handles any message type we did not list: reply that it is unsupported.
                default -> ExchangeCoordinator.CommandResult.reply(new InfoMessage(false, "Unsupported message type.", null));
            };
        }

        // Handles a RegisterRequest, enforcing that one connection can only register once.
        private ExchangeCoordinator.CommandResult handleRegister(RegisterRequest request) {
            // If "username" is already set, this connection registered before; reject the second attempt.
            if (username != null) {
                return ExchangeCoordinator.CommandResult.reply(new InfoMessage(false, "This client is already registered as " + username + ".", null));
            }

            // Ask the coordinator to actually register the user (it checks the name is free, creates an album, etc.).
            ExchangeCoordinator.CommandResult result = coordinator.register(request, this);
            // If the coordinator's reply is a successful RegisterResponse, remember the username on this handler.
            // The pattern-matching "instanceof" both checks the type and gives us "registerResponse" to inspect.
            if (result.directMessage() instanceof RegisterResponse registerResponse && registerResponse.success()) {
                username = request.username().trim();
            }
            // Pass the coordinator's reply back to the caller to be sent.
            return result;
        }

        // Guard helper: only run the supplied action if the user has registered; otherwise return an error reply.
        private ExchangeCoordinator.CommandResult ensureRegistered(CheckedResultSupplier supplier) {
            // No username means the client skipped registration.
            if (username == null) {
                return ExchangeCoordinator.CommandResult.reply(new InfoMessage(false, "Register before sending other requests.", null));
            }
            // The user is registered, so run the deferred action and return its result. ".get()" executes the lambda.
            return supplier.get();
        }

        // Sends out everything contained in a CommandResult: the direct reply plus any follow-up messages aimed
        // at OTHER users (e.g. notifying the recipient of a trade).
        private void dispatch(ExchangeCoordinator.CommandResult result) {
            // Nothing to do if there is no result.
            if (result == null) {
                return;
            }
            // If there is a direct reply for THIS client, send it.
            if (result.directMessage() != null) {
                send(result.directMessage());
            }
            // Loop over each follow-up message and send it through the connection it is addressed to. Each follow-up
            // knows which client it belongs to, which may be a different user than this handler's client.
            for (ExchangeCoordinator.DispatchMessage followUp : result.followUpMessages()) {
                followUp.connection().send(followUp.message());
            }
        }

        // "@FunctionalInterface" marks an interface with exactly one method, so it can be implemented by a lambda.
        // This little interface represents "some action that returns a CommandResult", used by ensureRegistered.
        @FunctionalInterface
        private interface CheckedResultSupplier {
            // The single method: run the action and return its CommandResult.
            ExchangeCoordinator.CommandResult get();
        }
    }
}
