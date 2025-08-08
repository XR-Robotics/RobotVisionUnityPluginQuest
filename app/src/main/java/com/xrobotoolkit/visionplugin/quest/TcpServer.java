package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TcpServer {

    // Callback Interface Definition
    public interface ServerCallback {
        void onServerStarted(int port);

        void onClientConnected(Socket clientSocket); // Pass the socket for more info

        void onClientDisconnected();

        void onDataReceived(byte[] data, int length);

        void onError(String errorMessage, Exception e);

        void onServerStopped();
    }

    private final String TAG = "TcpServer";
    private Thread tcpThread;
    private volatile boolean receive = false; // Make it volatile for thread safety

    private ServerCallback serverCallback; // Store the callback

    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream inputStream;
    private DataInputStream dataInputStream;

    // Updated StartTCPServer method
    public void startTCPServer(int port, ServerCallback callback) throws IOException { // Added ServerCallback parameter
        // Prevent starting multiple server instances
        if (isServerRunning()) {
            throw new IllegalStateException("TCP Server is already running");
        }

        this.serverCallback = callback; // Store the callback instance
        receive = true;
        Log.i(TAG, "startTCPServer:" + port);

        if (serverCallback != null) {
            serverCallback.onServerStarted(port); // Notify: Server is starting
        }

        tcpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "-ServerSocket:" + port);
                    serverSocket = new ServerSocket(port);

                    // Loop to accept multiple client connections sequentially
                    while (receive) {
                        Log.i(TAG, "Waiting for client connection...");

                        try {
                            socket = serverSocket.accept();
                            Log.i(TAG, "Socket connected:" + socket.getInetAddress().getHostAddress());
                            if (serverCallback != null) {
                                serverCallback.onClientConnected(socket); // Notify: Client connected
                            }

                            inputStream = socket.getInputStream();
                            dataInputStream = new DataInputStream(inputStream);

                            // Handle communication with current client
                            handleClientCommunication();

                        } catch (IOException e) {
                            if (receive) {
                                Log.e(TAG, "Error accepting client connection", e);
                                if (serverCallback != null) {
                                    serverCallback.onError("Error accepting connection", e);
                                }
                            }
                            // Continue to next iteration to accept new connections
                        } finally {
                            // Cleanup current client connection
                            cleanupClientConnection();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException creating server socket", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Server socket creation error", e);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception in TCP Server main loop", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Server Runtime Error", e);
                    }
                } finally {
                    cleanupServer();
                }
            }
        });
        tcpThread.setName("TcpServerThread-" + port); // Good practice to name threads
        tcpThread.start();
    }

    private void handleClientCommunication() {
        try {
            while (receive) { // Handle communication with current client
                // Check if socket is still connected before attempting to read
                if (socket.isClosed() || !socket.isConnected() || socket.isInputShutdown()) {
                    Log.i(TAG, "Socket is no longer connected, exiting read loop");
                    break;
                }

                // Read the 4-byte header and obtain the length of the packet body
                byte[] header = new byte[4];
                try {
                    dataInputStream.readFully(header);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read header, client might have disconnected.", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Failed to read header", e);
                    }
                    break; // Exit loop if cannot read header - client likely disconnected
                }

                // Analyze package length (big end mode)
                ByteBuffer buffer = ByteBuffer.wrap(header);
                buffer.order(ByteOrder.BIG_ENDIAN);
                int bodyLength = buffer.getInt();
                Log.i(TAG, "Received packet length: " + bodyLength);

                if (bodyLength <= 0 || bodyLength > 10 * 1024 * 1024) { // Basic sanity check for length
                    Log.w(TAG, "Invalid body length received: " + bodyLength);
                    if (serverCallback != null) {
                        serverCallback.onError("Invalid body length: " + bodyLength, null);
                    }
                    continue; // Potentially corrupt data, skip processing this connection
                }

                byte[] body = new byte[bodyLength];
                try {
                    dataInputStream.readFully(body); // Ensure complete reading
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read body, client might have disconnected.", e);
                    if (serverCallback != null) {
                        serverCallback.onError("Failed to read body", e);
                    }
                    break; // Exit loop if cannot read body - client likely disconnected
                }

                if (!receive) { // Check again before processing
                    break;
                }

                Log.i(TAG, "inputStream.read (body bytes): " + bodyLength);
                if (bodyLength > 0) {
                    // Notify: Data received
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
                    serverCallback.onClientDisconnected(); // Notify: Client disconnected
                }
            }
            socket = null;
        } catch (IOException e) {
            Log.e(TAG, "IOException during client cleanup", e);
            if (serverCallback != null) {
                serverCallback.onError("Error during client cleanup", e);
            }
        }
    }

    private void cleanupServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.i(TAG, "Server socket closed");
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException during server cleanup", e);
        }
        receive = false; // Ensure loop terminates
        Log.i(TAG, "TCP Server thread finished.");
        if (serverCallback != null) {
            serverCallback.onServerStopped(); // Notify: Server thread stopped
        }
    }

    // Add a method to stop the server gracefully
    public void stopServer() throws InterruptedException {
        Log.i(TAG, "Attempting to stop TCP server...");
        receive = false; // Signal the loop to terminate

        // Interrupt the thread first to break out of blocking I/O operations
        if (tcpThread != null) {
            tcpThread.interrupt();
        }

        try {
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

        if (tcpThread != null) {
            try {
                tcpThread.join(1000); // Wait for the thread to die for up to 1 second
                if (tcpThread.isAlive()) {
                    Log.w(TAG, "TCP thread did not terminate in time. Forcing shutdown...");
                    // You might want to take additional action here, like closing sockets
                } else {
                    Log.i(TAG, "TCP thread stopped successfully.");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for TCP thread to stop.", e);
                throw e; // rethrow to signal the caller
            }
        }
    }

    // Method to check if the server is running
    public boolean isServerRunning() {
        return receive && tcpThread != null && tcpThread.isAlive();
    }

    // Method to check if a client is connected
    public boolean isClientConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}