// The "client.net" package holds the client's networking code (talking to the server).
package com.stickerexchange.client.net;

// The shared parent type of every network message.
import com.stickerexchange.common.protocol.Message;
// "EOFException" (End Of File) signals the server closed the connection.
import java.io.EOFException;
// General input/output error type.
import java.io.IOException;
// Streams that read/write whole Java objects over the socket.
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
// "Socket" is one network connection to the server.
import java.net.Socket;

/**
 * ServerConnection — the client's NETWORK LAYER. It hides all the messy socket details behind a few
 * simple methods so the rest of the client can just "send a message" or "be told when one arrives".
 *
 * <p>WHAT IT DOES:
 * <ul>
 *   <li>{@code connect()} opens a socket to the server and starts a background thread that listens for
 *       incoming messages.</li>
 *   <li>{@code send(...)} writes a message to the server.</li>
 *   <li>incoming messages are pushed to a {@link MessageListener} (the ClientController).</li>
 * </ul>
 *
 * <p>KEY CONCEPT — background thread: reading from the network BLOCKS (waits) until data arrives. If we
 * did that on the UI thread, the window would freeze. So we read on a separate "reader" thread and the
 * UI stays responsive.
 *
 * <p>KEY CONCEPT — listener / callback: rather than this class knowing about the UI, it calls back into
 * whoever registered as its MessageListener. This keeps networking and UI nicely decoupled.
 */
// Networking helper; "final" prevents extension.
public final class ServerConnection {
    // Where to connect: server address and port. "final" = fixed once the object is built.
    private final String host;
    private final int port;
    // Who to notify about incoming messages and disconnects.
    private final MessageListener listener;

    // The live connection and its streams. These start as null and are created in connect().
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    // The background thread that reads incoming messages.
    private Thread readerThread;

    // Constructor: remember where to connect and who to call back, but do NOT connect yet.
    public ServerConnection(String host, int port, MessageListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    // Opens the connection. "throws IOException" because networking can fail (e.g. server not running).
    public void connect() throws IOException {
        // Create the socket, which actually dials the server at host:port.
        socket = new Socket(host, port);
        // Send small messages immediately instead of buffering them, for responsiveness.
        socket.setTcpNoDelay(true);
        // Set up the OUTPUT stream first and flush its header (the server's input stream waits for it).
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        // Then set up the INPUT stream to receive objects from the server.
        inputStream = new ObjectInputStream(socket.getInputStream());

        // Create a background thread whose job is to run our readLoop method. "this::readLoop" is a method
        // reference; "server-reader" is just a human-friendly thread name.
        readerThread = new Thread(this::readLoop, "server-reader");
        // Mark it as a "daemon" thread so it will not stop the program from exiting when the app closes.
        readerThread.setDaemon(true);
        // Start the thread running.
        readerThread.start();
    }

    // Sends a message to the server. "synchronized" stops two threads writing at once and garbling data.
    public synchronized void send(Message message) throws IOException {
        // Serialize and write the object to the server.
        outputStream.writeObject(message);
        // Flush to push it out over the network right away.
        outputStream.flush();
    }

    // Closes the connection cleanly. Safe to call even if already closed.
    public void close() {
        try {
            // Only close if a socket was actually created.
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // If closing fails there is nothing useful to do, so we deliberately ignore the error.
        }
    }

    // The loop that runs on the background thread, continuously reading messages from the server.
    private void readLoop() {
        try {
            // Keep reading forever until the connection ends or an error occurs.
            while (true) {
                // Wait for and read the next object the server sends.
                Object payload = inputStream.readObject();
                // If it is a Message (pattern-matching instanceof also assigns it to "message"), hand it to the listener.
                if (payload instanceof Message message) {
                    listener.onMessageReceived(message);
                }
            }
        } catch (EOFException exception) {
            // EOFException means the server closed the connection normally; report a friendly disconnect reason.
            listener.onDisconnected("Server connection was closed.");
        } catch (IOException | ClassNotFoundException exception) {
            // Any other failure: report the disconnect with the error's message. "|" handles both error types here.
            listener.onDisconnected(exception.getMessage());
        }
    }

    // A small interface defining the two callbacks the network layer makes. The ClientController implements
    // it. This is how incoming messages and disconnects reach the rest of the app.
    public interface MessageListener {
        // Called whenever a message arrives from the server.
        void onMessageReceived(Message message);

        // Called when the connection is lost, with a human-readable reason.
        void onDisconnected(String reason);
    }
}
