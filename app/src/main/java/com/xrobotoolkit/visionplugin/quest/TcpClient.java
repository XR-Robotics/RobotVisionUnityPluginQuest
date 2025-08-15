package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TcpClient - Asynchronous TCP Client with Protocol Support
 * 
 * This class provides a robust TCP client implementation with support for
 * length-prefixed message protocols commonly used in video streaming
 * applications.
 * It handles connection management, data transmission, and reception on
 * background threads.
 * 
 * Protocol Format:
 * - 4-byte big-endian length header
 * - Variable-length data payload
 * - Automatic reconnection and error handling
 * 
 * Key Features:
 * - Asynchronous connection and data handling
 * - Thread-safe operations with proper cleanup
 * - Callback-based event notification system
 * - Automatic connection state management
 * - Robust error handling and recovery
 * 
 * Usage Pattern:
 * 1. Implement ClientCallback interface for event handling
 * 2. Call connectToServer() with host, port, and callback
 * 3. Use send() method to transmit data
 * 4. Handle received data via onDataReceived() callback
 * 5. Call disconnect() when finished
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class TcpClient {

    /**
     * Callback interface for TCP client events.
     * Implement this interface to handle connection state changes and data
     * reception.
     */
    public interface ClientCallback {
        /**
         * Called when the client successfully connects to the server.
         */
        void onConnected();

        /**
         * Called when the client disconnects from the server.
         */
        void onDisconnected();

        /**
         * Called when data is received from the server.
         * 
         * @param data   Received data bytes
         * @param length Number of valid bytes in the data array
         */
        void onDataReceived(byte[] data, int length);

        /**
         * Called when an error occurs during client operations.
         * 
         * @param errorMessage Descriptive error message
         * @param e            Exception that caused the error (may be null)
         */
        void onError(String errorMessage, Exception e);
    }

    private final String TAG = "TcpClient";

    // Threading and connection management
    private Thread clientThread; // Background thread for network I/O
    private volatile boolean running = false; // Thread-safe running flag

    // Network connection components
    private Socket socket; // TCP socket connection
    private DataOutputStream outputStream; // Output stream for sending data
    private InputStream inputStream; // Input stream for receiving data

    // Event callback handler
    private ClientCallback clientCallback;

    /**
     * Connects to a TCP server asynchronously.
     * Creates a background thread to handle the connection and data reception.
     * All callback methods will be invoked from the client thread.
     * 
     * @param host     Server hostname or IP address
     * @param port     Server port number
     * @param callback Callback interface for handling client events
     */
    public void connectToServer(String host, int port, ClientCallback callback) {
        this.clientCallback = callback;
        running = true;

        clientThread = new Thread(() -> {
            try {
                Log.i(TAG, "Connecting to server: " + host + ":" + port);

                // Establish TCP connection
                socket = new Socket(host, port);
                outputStream = new DataOutputStream(socket.getOutputStream());
                inputStream = socket.getInputStream();

                // Notify successful connection
                if (clientCallback != null) {
                    clientCallback.onConnected();
                }

                // Main data reception loop
                while (running && !socket.isClosed()) {
                    try {
                        // Read 4-byte length header (big-endian format)
                        byte[] header = new byte[4];
                        int headerBytesRead = inputStream.read(header);
                        if (headerBytesRead == -1) {
                            break; // Connection closed by server
                        }

                        // Parse message length from header
                        ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
                        int length = headerBuffer.getInt();

                        // Validate message length to prevent buffer overflow attacks
                        if (length <= 0 || length > 10 * 1024 * 1024) { // Max 10MB per message
                            Log.w(TAG, "Invalid packet length received: " + length);
                            break;
                        }

                        // Read message payload
                        byte[] data = new byte[length];
                        int totalRead = 0;
                        while (totalRead < length && running) {
                            int bytesRead = inputStream.read(data, totalRead, length - totalRead);
                            if (bytesRead == -1) {
                                break; // Connection closed during read
                            }
                            totalRead += bytesRead;
                        }

                        // Notify callback with received data
                        if (totalRead == length && clientCallback != null) {
                            clientCallback.onDataReceived(data, totalRead);
                        }
                    } catch (IOException e) {
                        if (running) { // Only report errors if we're still supposed to be running
                            Log.e(TAG, "Error during data reception", e);
                            if (clientCallback != null) {
                                clientCallback.onError("Data reception error", e);
                            }
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Client connection error", e);
                if (clientCallback != null) {
                    clientCallback.onError("Connection error", e);
                }
            } finally {
                disconnect(); // Ensure proper cleanup
            }
        });

        clientThread.setName("TcpClientThread-" + port);
        clientThread.start();
    }

    /**
     * Sends data to the connected server using length-prefixed protocol.
     * The data is automatically prefixed with a 4-byte big-endian length header.
     * 
     * @param data Data bytes to send (must not be null)
     */
    public void send(byte[] data) {
        if (outputStream != null && data != null) {
            try {
                // Prepare message with 4-byte length prefix
                ByteBuffer buffer = ByteBuffer.allocate(4 + data.length).order(ByteOrder.BIG_ENDIAN);
                buffer.putInt(data.length); // Length header
                buffer.put(data); // Payload data

                // Send complete message atomically
                outputStream.write(buffer.array());
                outputStream.flush();
                Log.i(TAG, "Sent data of length: " + data.length);
            } catch (IOException e) {
                Log.e(TAG, "Failed to send data", e);
                if (clientCallback != null) {
                    clientCallback.onError("Send failed", e);
                }
            }
        }
    }

    /**
     * Disconnects from the server and cleans up all resources.
     * This method is thread-safe and can be called multiple times safely.
     * All streams and sockets are properly closed, and callback notifications are
     * sent.
     */
    public void disconnect() {
        running = false;

        try {
            // Close all streams and socket resources
            if (outputStream != null)
                outputStream.close();
            if (inputStream != null)
                inputStream.close();
            if (socket != null && !socket.isClosed())
                socket.close();

            Log.i(TAG, "Disconnected from server.");

            // Notify callback of disconnection
            if (clientCallback != null) {
                clientCallback.onDisconnected();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error while disconnecting", e);
            if (clientCallback != null) {
                clientCallback.onError("Disconnection Error", e);
            }
        }
    }
}
