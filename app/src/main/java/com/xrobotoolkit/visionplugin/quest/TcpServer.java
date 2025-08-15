package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TcpServer - Multi-Client TCP Server with Protocol Support
 * 
 * This class implements a robust TCP server that can handle multiple client
 * connections
 * sequentially, with support for length-prefixed message protocols commonly
 * used in
 * video streaming applications. It provides comprehensive error handling,
 * connection
 * management, and callback-based event notification.
 * 
 * Protocol Format:
 * - 4-byte big-endian length header
 * - Variable-length data payload
 * - Automatic connection state management
 * - Sequential client handling (one client at a time)
 * 
 * Key Features:
 * - Asynchronous server operation on background thread
 * - Sequential multi-client support with automatic reconnection
 * - Comprehensive callback system for all server events
 * - Robust error handling with detailed error reporting
 * - Thread-safe operations with proper resource cleanup
 * - Configurable message size limits for security
 * 
 * Usage Pattern:
 * 1. Implement ServerCallback interface for event handling
 * 2. Call startTCPServer() with port and callback
 * 3. Handle client connections via callback methods
 * 4. Process received data in onDataReceived() callback
 * 5. Call stopServer() when finished
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class TcpServer {

    /**
     * Callback interface for TCP server events.
     * Implement this interface to handle server lifecycle and client interactions.
     */
    public interface ServerCallback {
        /**
         * Called when the server starts listening on the specified port.
         * 
         * @param port The port number the server is listening on
         */
        void onServerStarted(int port);

        /**
         * Called when a client connects to the server.
         * 
         * @param clientSocket The socket representing the connected client
         */
        void onClientConnected(Socket clientSocket);

        /**
         * Called when a client disconnects from the server.
         */
        void onClientDisconnected();

        /**
         * Called when data is received from a connected client.
         * 
         * @param data   Received data bytes
         * @param length Number of valid bytes in the data array
         */
        void onDataReceived(byte[] data, int length);

        /**
         * Called when an error occurs during server operations.
         * 
         * @param errorMessage Descriptive error message
         * @param e            Exception that caused the error (may be null)
         */
        void onError(String errorMessage, Exception e);

        /**
         * Called when the server stops and the background thread terminates.
         */
        void onServerStopped();
    }

    private final String TAG = "TcpServer";

    // Threading and server management
    private Thread tcpThread; // Background thread for server operations
    private volatile boolean receive = false; // Thread-safe flag for server state

    // Network connection components
    private ServerSocket serverSocket; // Server socket for accepting connections
    private Socket socket; // Current client socket connection
    private InputStream inputStream; // Input stream for receiving data
    private DataInputStream dataInputStream; // Buffered input stream for protocol parsing

    // Event callback handler
    private ServerCallback serverCallback;

    /**
     * Starts the TCP server on the specified port.
     * Creates a background thread to handle client connections and data reception.
     * The server will accept client connections sequentially (one at a time).
     * 
     * @param port     Port number to listen on (1-65535)
     * @param callback Callback interface for handling server events
     * @throws IOException           if server socket creation fails
     * @throws IllegalStateException if server is already running
     */
    public void startTCPServer(int port, ServerCallback callback) throws IOException {
        // Prevent starting multiple server instances
        if (isServerRunning()) {
            throw new IllegalStateException("TCP Server is already running");
        }

        this.serverCallback = callback;
        receive = true;
        Log.i(TAG, "Starting TCP server on port: " + port);

        // Notify that server is starting
        if (serverCallback != null) {
            serverCallback.onServerStarted(port);
        }

        tcpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Creating server socket on port: " + port);
                    serverSocket = new ServerSocket(port);

                    // Main server loop - accept multiple client connections sequentially
                    while (receive) {
                        Log.i(TAG, "Waiting for client connection...");

                        try {
                            // Accept incoming client connection
                            socket = serverSocket.accept();
                            Log.i(TAG, "Client connected from: " + socket.getInetAddress().getHostAddress());

                            // Notify callback of client connection
                            if (serverCallback != null) {
                                serverCallback.onClientConnected(socket);
                            }

                            // Set up input streams for data reception
                            inputStream = socket.getInputStream();
                            dataInputStream = new DataInputStream(inputStream);

                            // Handle communication with current client
                            handleClientCommunication();

                        } catch (IOException e) {
                            if (receive) { // Only report errors if server is still supposed to be running
                                Log.e(TAG, "Error accepting client connection", e);
                                if (serverCallback != null) {
                                    serverCallback.onError("Error accepting connection", e);
                                }
                            }
                            // Continue to next iteration to accept new connections
                        } finally {
                            // Cleanup current client connection before accepting next
                            cleanupClientConnection();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error creating server socket", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Server socket creation error", e);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in TCP server main loop", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Server Runtime Error", e);
                    }
                } finally {
                    // Ensure complete server cleanup on thread termination
                    cleanupServer();
                }
            }
        });
        tcpThread.setName("TcpServerThread-" + port); // Name thread for easier debugging
        tcpThread.start();
    }

    /**
     * Handles communication with the currently connected client.
     * Implements the length-prefixed message protocol:
     * 1. Reads 4-byte big-endian length header
     * 2. Reads variable-length message payload
     * 3. Notifies callback with received data
     * 4. Continues until client disconnects or error occurs
     */
    private void handleClientCommunication() {
        try {
            while (receive) { // Main communication loop for current client
                // Check if socket is still connected before attempting to read
                if (socket.isClosed() || !socket.isConnected() || socket.isInputShutdown()) {
                    Log.i(TAG, "Socket is no longer connected, exiting read loop");
                    break;
                }

                // Read the 4-byte length header
                byte[] header = new byte[4];
                try {
                    dataInputStream.readFully(header);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read header, client might have disconnected.", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Failed to read header", e);
                    }
                    break; // Exit loop - client likely disconnected
                }

                // Parse message length from header (big-endian format)
                ByteBuffer buffer = ByteBuffer.wrap(header);
                buffer.order(ByteOrder.BIG_ENDIAN);
                int bodyLength = buffer.getInt();
                Log.i(TAG, "Received packet length: " + bodyLength);

                // Validate message length to prevent buffer overflow and DoS attacks
                if (bodyLength <= 0 || bodyLength > 10 * 1024 * 1024) { // Max 10MB per message
                    Log.w(TAG, "Invalid body length received: " + bodyLength);
                    if (serverCallback != null) {
                        serverCallback.onError("Invalid body length: " + bodyLength, null);
                    }
                    continue; // Skip this message but continue with connection
                }

                // Read message payload
                byte[] body = new byte[bodyLength];
                try {
                    dataInputStream.readFully(body); // Ensure complete message reading
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read body, client might have disconnected.", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Failed to read body", e);
                    }
                    break; // Exit loop - client likely disconnected
                }

                // Check if server is still supposed to be running
                if (!receive) {
                    break;
                }

                Log.i(TAG, "Received message body: " + bodyLength + " bytes");
                if (bodyLength > 0) {
                    // Notify callback with received data
                    if (serverCallback != null) {
                        serverCallback.onDataReceived(body, bodyLength);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in client communication", e);
            if (serverCallback != null) {
                serverCallback.onError("Client communication error", e);
            }
        }
    }

    /**
     * Cleans up resources associated with the current client connection.
     * Closes streams and socket, and notifies callback of disconnection.
     */
    private void cleanupClientConnection() {
        try {
            if (dataInputStream != null) {
                dataInputStream.close();
                dataInputStream = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                Log.i(TAG, "Client socket closed");
                if (serverCallback != null) {
                    serverCallback.onClientDisconnected();
                }
            }
            socket = null;
        } catch (IOException e) {
            Log.e(TAG, "Error during client cleanup", e);
            if (serverCallback != null) {
                serverCallback.onError("Error during client cleanup", e);
            }
        }
    }

    /**
     * Cleans up server-level resources including the server socket.
     * Called when the server thread terminates.
     */
    private void cleanupServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.i(TAG, "Server socket closed");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error during server cleanup", e);
        }

        receive = false; // Ensure loop termination
        Log.i(TAG, "TCP Server thread finished.");

        if (serverCallback != null) {
            serverCallback.onServerStopped();
        }
    }

    /**
     * Stops the server gracefully and cleans up all resources.
     * This method is thread-safe and will interrupt blocking I/O operations.
     * 
     * @throws InterruptedException if interrupted while waiting for thread
     *                              termination
     */
    public void stopServer() throws InterruptedException {
        Log.i(TAG, "Attempting to stop TCP server...");
        receive = false; // Signal all loops to terminate

        // Interrupt the thread to break out of blocking I/O operations
        if (tcpThread != null) {
            tcpThread.interrupt();
        }

        try {
            // Close all network resources to unblock any pending operations
            if (dataInputStream != null)
                dataInputStream.close();
            if (inputStream != null)
                inputStream.close();
            if (socket != null && !socket.isClosed())
                socket.close();
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
            Log.i(TAG, "Stopped TCP server");
        } catch (IOException e) {
            Log.e(TAG, "Error while stopping server", e);
        }

        // Wait for the server thread to terminate
        if (tcpThread != null) {
            try {
                tcpThread.join(1000); // Wait up to 1 second for graceful termination
                if (tcpThread.isAlive()) {
                    Log.w(TAG, "TCP thread did not terminate in time. Forcing shutdown...");
                } else {
                    Log.i(TAG, "TCP thread stopped successfully.");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for TCP thread to stop.", e);
                throw e; // Re-throw to signal caller
            }
        }
    }

    /**
     * Checks if the server is currently running and accepting connections.
     * 
     * @return true if server is running, false otherwise
     */
    public boolean isServerRunning() {
        return receive && tcpThread != null && tcpThread.isAlive();
    }

    /**
     * Checks if a client is currently connected to the server.
     * 
     * @return true if a client is connected, false otherwise
     */
    public boolean isClientConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}