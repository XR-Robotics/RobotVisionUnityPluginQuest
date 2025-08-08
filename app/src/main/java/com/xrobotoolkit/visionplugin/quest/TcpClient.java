package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TcpClient {

    // Callback Interface
    public interface ClientCallback {
        void onConnected();

        void onDisconnected();

        void onDataReceived(byte[] data, int length);

        void onError(String errorMessage, Exception e);
    }
    
    private final String TAG = "TcpClient";
    private Thread clientThread;
    private boolean running = false;
    private Socket socket;
    private DataOutputStream outputStream;
    private InputStream inputStream;

    private ClientCallback clientCallback;

    public void connectToServer(String host, int port, ClientCallback callback) {
        this.clientCallback = callback;
        running = true;

        clientThread = new Thread(() -> {
            try {
                Log.i(TAG, "Connecting to server: " + host + ":" + port);
                socket = new Socket(host, port);
                outputStream = new DataOutputStream(socket.getOutputStream());
                inputStream = socket.getInputStream();

                if (clientCallback != null) {
                    clientCallback.onConnected();
                }

                while (running && !socket.isClosed()) {
                    // Read 4-byte header
                    byte[] header = new byte[4];
                    int read = inputStream.read(header);
                    if (read == -1) break;

                    ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
                    int length = headerBuffer.getInt();
                    if (length <= 0 || length > 10 * 1024 * 1024) {
                        Log.w(TAG, "Invalid packet length received: " + length);
                        break;
                    }

                    byte[] data = new byte[length];
                    int totalRead = 0;
                    while (totalRead < length) {
                        int bytesRead = inputStream.read(data, totalRead, length - totalRead);
                        if (bytesRead == -1) break;
                        totalRead += bytesRead;
                    }

                    if (clientCallback != null) {
                        clientCallback.onDataReceived(data, totalRead);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Client IO Error", e);
                if (clientCallback != null) {
                    clientCallback.onError("Client IO Error", e);
                }
            } finally {
                disconnect(); // Clean up
            }
        });

        clientThread.setName("TcpClientThread-" + port);
        clientThread.start();
    }

    public void send(byte[] data) {
        if (outputStream != null && data != null) {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(4 + data.length).order(ByteOrder.BIG_ENDIAN);
                buffer.putInt(data.length);
                buffer.put(data);
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

    public void disconnect() {
        running = false;
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null && !socket.isClosed()) socket.close();
            Log.i(TAG, "Disconnected from server.");
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
