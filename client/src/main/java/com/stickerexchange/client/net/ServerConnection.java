package com.stickerexchange.client.net;

import com.stickerexchange.common.protocol.Message;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public final class ServerConnection {
    private final String host;
    private final int port;
    private final MessageListener listener;

    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Thread readerThread;

    public ServerConnection(String host, int port, MessageListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(socket.getInputStream());

        readerThread = new Thread(this::readLoop, "server-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void send(Message message) throws IOException {
        outputStream.writeObject(message);
        outputStream.flush();
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void readLoop() {
        try {
            while (true) {
                Object payload = inputStream.readObject();
                if (payload instanceof Message message) {
                    listener.onMessageReceived(message);
                }
            }
        } catch (EOFException exception) {
            listener.onDisconnected("Server connection was closed.");
        } catch (IOException | ClassNotFoundException exception) {
            listener.onDisconnected(exception.getMessage());
        }
    }

    public interface MessageListener {
        void onMessageReceived(Message message);

        void onDisconnected(String reason);
    }
}
