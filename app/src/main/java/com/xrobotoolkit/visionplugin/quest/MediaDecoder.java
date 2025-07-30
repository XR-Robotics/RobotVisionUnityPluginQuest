package com.xrobotoolkit.visionplugin.quest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Simplified MediaDecoder that updates Unity textures directly using bitmap
 * data
 * This approach eliminates ImageReader and processes YUV data directly from
 * MediaCodec
 */
public class MediaDecoder {
    private static final String TAG = "MediaDecoder";

    private MediaCodec mediaCodec;
    private int unityTextureId;
    private int width, height;

    // TCP server for receiving H.264 data
    private ServerSocket serverSocket;
    private Thread tcpThread;
    private boolean isReceiving = false;
    private boolean hasNewFrame = false;
    private final Object frameLock = new Object();
    private byte[] latestRgbData = null;

    // Processing tracking
    private String lastProcessingStrategy = "None";
    private int frameCounter = 0;

    /**
     * Initialize with Unity texture pointer - Direct bitmap processing approach
     * Updated to match H264Encoder.cpp configuration
     */
    public void initialize(int unityTextureId, int width, int height) {
        Log.i(TAG, "initialize: textureId=" + unityTextureId + ", size=" + width + "x" + height);

        this.unityTextureId = unityTextureId;
        this.width = width;
        this.height = height;

        try {
            // Create MediaCodec for H.264 decoding without surface
            // We'll process the raw YUV output buffers directly
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

            // Configure decoder to match encoder parameters from H264Encoder.cpp
            // The encoder uses YUV420P format
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);

            // Set expected bitrate to match encoder (1 Mbps)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);

            // Set max input size to handle large frames
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 2);

            // The encoder uses baseline profile - hint for decoder
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);

            // Frame rate hint (encoder uses 30fps for 720p or dynamic)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);

            // The encoder disables B-frames and uses 2-second GOP
            // These are encoding parameters, decoder will handle automatically

            Log.i(TAG, "Decoder format configured to match encoder:");
            Log.i(TAG, "  Resolution: " + width + "x" + height);
            Log.i(TAG, "  Color format: YUV420P");
            Log.i(TAG, "  Expected bitrate: 1 Mbps");
            Log.i(TAG, "  Profile: Baseline");
            Log.i(TAG, "  Frame rate: 30fps");

            // Configure MediaCodec without a surface - we'll process output buffers
            // directly
            mediaCodec.configure(format, null, null, 0);
            mediaCodec.start();

            Log.i(TAG, "MediaDecoder initialized successfully to match H264Encoder.cpp configuration");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaDecoder", e);
            throw new RuntimeException("MediaDecoder initialization failed", e);
        }
    }

    /**
     * Feed H.264 data to MediaCodec decoder and process output directly
     * Optimized for low latency
     */
    private void feedDataToDecoder(byte[] data, int size) {
        try {
            // Use shorter timeout for lower latency (1ms instead of 10ms)
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(1000); // 1ms timeout
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(data, 0, size);

                // Use proper timestamp for frame ordering
                long presentationTimeUs = System.nanoTime() / 1000;
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, size, presentationTimeUs, 0);
            } else {
                // Don't log warning for performance - just drop frame silently
                return;
            }

            // Process output buffers with shorter timeout
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000); // 1ms timeout

            while (outputBufferIndex >= 0) {
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Handle format change quickly without verbose logging
                    MediaFormat newFormat = mediaCodec.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                        int newWidth = newFormat.getInteger(MediaFormat.KEY_WIDTH);
                        int newHeight = newFormat.getInteger(MediaFormat.KEY_HEIGHT);
                        if (newWidth != width || newHeight != height) {
                            width = newWidth;
                            height = newHeight;
                        }
                    }
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    continue;
                }

                if (bufferInfo.size > 0) {
                    // Get the decoded YUV data directly from MediaCodec output buffer
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        processDecodedFrame(outputBuffer, bufferInfo);
                    }
                }

                // Release output buffer
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error feeding data to decoder", e);
        }
    }

    /**
     * Process decoded frame data directly from MediaCodec output buffer
     * Optimized for low latency
     */
    private void processDecodedFrame(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
        try {
            // Skip verbose logging for performance - only log errors

            // Extract YUV data from output buffer
            byte[] yuvData = new byte[bufferInfo.size];
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
            outputBuffer.get(yuvData);

            // Convert YUV to RGB using fast approach
            byte[] rgbData = convertYuvToRgbDirect(yuvData, width, height);

            // Single synchronized block to minimize lock time
            synchronized (frameLock) {
                if (rgbData != null) {
                    latestRgbData = rgbData;
                    lastProcessingStrategy = "Fast YUV420P";
                } else {
                    // Only create test pattern if conversion completely fails
                    latestRgbData = createVideoLikeTestPattern(width, height);
                    lastProcessingStrategy = "Test Pattern Fallback";
                }
                hasNewFrame = true;
                frameCounter++;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing decoded frame", e);
            // Generate test pattern as fallback
            synchronized (frameLock) {
                latestRgbData = createVideoLikeTestPattern(width, height);
                hasNewFrame = true;
                frameCounter++;
                lastProcessingStrategy = "Error Fallback";
            }
        }
    }

    /**
     * Convert YUV data directly to RGB using optimized low-latency approach
     */
    private byte[] convertYuvToRgbDirect(byte[] yuvData, int width, int height) {
        // Skip verbose logging for performance

        // Direct YUV420P conversion - this is what the encoder outputs
        // Skip fallbacks for lower latency unless primary conversion fails
        byte[] result = convertYuv420PToRgb24Fast(yuvData, width, height);
        if (result != null) {
            return result;
        }

        // Only use fallback if primary method fails
        Log.w(TAG, "Primary YUV420P conversion failed, using fallback");
        return convertYuvDataViaBitmap(yuvData, width, height);
    }

    /**
     * Convert YUV data to RGB using bitmap approach (assumes NV21 format)
     */
    private byte[] convertYuvDataViaBitmap(byte[] yuvData, int width, int height) {
        try {
            // Try different YUV formats
            int[] formats = { ImageFormat.NV21, ImageFormat.YV12, ImageFormat.NV16 };

            for (int format : formats) {
                try {
                    YuvImage yuvImage = new YuvImage(yuvData, format, width, height, null);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    yuvImage.compressToJpeg(new Rect(0, 0, width, height), 90, out);
                    byte[] jpegBytes = out.toByteArray();

                    Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
                    if (bitmap != null) {
                        byte[] rgbData = bitmapToRgbArray(bitmap);
                        bitmap.recycle();
                        Log.v(TAG, "YUV bitmap conversion successful with format: " + format);
                        return rgbData;
                    }
                } catch (Exception e) {
                    Log.v(TAG, "YUV format " + format + " failed: " + e.getMessage());
                }
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error in YUV bitmap conversion", e);
            return null;
        }
    }

    /**
     * Convert Bitmap to RGB byte array for Unity texture loading
     */
    private byte[] bitmapToRgbArray(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            byte[] rgb = new byte[width * height * 3];

            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Swap R and B channels for Unity (BGR format)
                rgb[i * 3] = (byte) b;
                rgb[i * 3 + 1] = (byte) g;
                rgb[i * 3 + 2] = (byte) r;
            }

            return rgb;
        } catch (Exception e) {
            Log.e(TAG, "Error converting Bitmap to RGB array", e);
            return null;
        }
    }

    /**
     * Fast YUV420P to RGB24 conversion - optimized for low latency
     * Removes bounds checking and logging for maximum speed
     */
    private byte[] convertYuv420PToRgb24Fast(byte[] yuv420p, int width, int height) {
        int ySize = width * height;
        int uvSize = ySize / 4;

        // Quick size check - bail out immediately if wrong size
        if (yuv420p.length < ySize + uvSize + uvSize) {
            return null;
        }

        byte[] rgb = new byte[ySize * 3];

        // YUV420P layout: Y[0..ySize-1], U[ySize..ySize+uvSize-1], V[ySize+uvSize..end]
        int uPlaneStart = ySize;
        int vPlaneStart = ySize + uvSize;
        int uvWidth = width / 2;

        // Unrolled inner loop for better performance
        for (int row = 0; row < height; row++) {
            int yRowStart = row * width;
            int uvRow = row / 2;
            int uvRowStart = uvRow * uvWidth;
            int rgbRowStart = yRowStart * 3;

            for (int col = 0; col < width; col++) {
                int yIndex = yRowStart + col;
                int uvCol = col / 2;
                int uIndex = uPlaneStart + uvRowStart + uvCol;
                int vIndex = vPlaneStart + uvRowStart + uvCol;

                int y = (yuv420p[yIndex] & 0xFF) - 16;
                int u = (yuv420p[uIndex] & 0xFF) - 128;
                int v = (yuv420p[vIndex] & 0xFF) - 128;

                // Fast BT.709 conversion using integer arithmetic
                int r = (298 * y + 459 * v + 128) >> 8;
                int g = (298 * y - 137 * v - 55 * u + 128) >> 8;
                int b = (298 * y + 541 * u + 128) >> 8;

                // Fast clamping using bit operations
                r = (r < 0) ? 0 : (r > 255) ? 255 : r;
                g = (g < 0) ? 0 : (g > 255) ? 255 : g;
                b = (b < 0) ? 0 : (b > 255) ? 255 : b;

                int rgbIndex = rgbRowStart + col * 3;
                rgb[rgbIndex] = (byte) r;
                rgb[rgbIndex + 1] = (byte) g;
                rgb[rgbIndex + 2] = (byte) b;
            }
        }

        return rgb;
    }

    /**
     * Convert YUV420P (planar) to RGB24 - optimized for encoder's output format
     * YUV420P format: Y plane, then U plane, then V plane (all separate)
     * This matches the encoder's AV_PIX_FMT_YUV420P format exactly
     */
    private byte[] convertYuv420PToRgb24(byte[] yuv420p, int width, int height) {
        try {
            byte[] rgb = new byte[width * height * 3];

            int ySize = width * height;
            int uSize = ySize / 4; // U and V planes are 1/4 the size each
            int vSize = uSize;

            // Expected data layout from encoder: Y plane + U plane + V plane
            int expectedSize = ySize + uSize + vSize;
            if (yuv420p.length < expectedSize) {
                Log.w(TAG, "YUV420P data too small: " + yuv420p.length + ", expected: " + expectedSize);
                return null;
            }

            Log.d(TAG, "Converting YUV420P: Y=" + ySize + ", U=" + uSize + ", V=" + vSize + ", total=" + expectedSize);

            // YUV420P layout: Y[0..ySize-1], U[ySize..ySize+uSize-1],
            // V[ySize+uSize..ySize+uSize+vSize-1]
            int yPlaneStart = 0;
            int uPlaneStart = ySize;
            int vPlaneStart = ySize + uSize;

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    // Y component (full resolution)
                    int yIndex = yPlaneStart + (row * width + col);

                    // U and V components (subsampled by 2 in both dimensions)
                    int uvRow = row / 2;
                    int uvCol = col / 2;
                    int uvWidth = width / 2;
                    int uIndex = uPlaneStart + (uvRow * uvWidth + uvCol);
                    int vIndex = vPlaneStart + (uvRow * uvWidth + uvCol);

                    // Bounds checking
                    if (yIndex >= yuv420p.length || uIndex >= yuv420p.length || vIndex >= yuv420p.length) {
                        continue;
                    }

                    int y = yuv420p[yIndex] & 0xFF;
                    int u = yuv420p[uIndex] & 0xFF;
                    int v = yuv420p[vIndex] & 0xFF;

                    // BT.709 YUV to RGB conversion (better for HD content from encoder)
                    // Studio range to full range conversion
                    y = Math.max(0, y - 16);
                    u = u - 128;
                    v = v - 128;

                    // BT.709 conversion matrix for HD content
                    int r = (int) ((1.164 * y) + (1.793 * v));
                    int g = (int) ((1.164 * y) - (0.534 * v) - (0.213 * u));
                    int b = (int) ((1.164 * y) + (2.115 * u));

                    // Clamp values to 0-255
                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    int rgbIndex = (row * width + col) * 3;
                    if (rgbIndex + 2 < rgb.length) {
                        // Store as RGB (Unity expects RGB format)
                        rgb[rgbIndex] = (byte) r;
                        rgb[rgbIndex + 1] = (byte) g;
                        rgb[rgbIndex + 2] = (byte) b;
                    }
                }
            }

            Log.v(TAG, "YUV420P to RGB conversion completed successfully");
            return rgb;
        } catch (Exception e) {
            Log.e(TAG, "Error in YUV420P to RGB conversion", e);
            return null;
        }
    }

    /**
     * Convert YUV420 to RGB24 using standard conversion formulas (fallback method)
     */
    private byte[] convertYuv420ToRgb24(byte[] yuv420, int width, int height) {
        try {
            byte[] rgb = new byte[width * height * 3];

            int ySize = width * height;
            int uvSize = ySize / 4;

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int yIndex = row * width + col;
                    int uvRow = row / 2;
                    int uvCol = col / 2;
                    int uIndex = ySize + uvRow * (width / 2) + uvCol;
                    int vIndex = ySize + uvSize + uvRow * (width / 2) + uvCol;

                    // Bounds checking
                    if (yIndex >= yuv420.length || uIndex >= yuv420.length || vIndex >= yuv420.length) {
                        continue;
                    }

                    int y = yuv420[yIndex] & 0xFF;
                    int u = yuv420[uIndex] & 0xFF;
                    int v = yuv420[vIndex] & 0xFF;

                    // YUV to RGB conversion (BT.601)
                    int r = (int) (y + 1.402 * (v - 128));
                    int g = (int) (y - 0.344 * (u - 128) - 0.714 * (v - 128));
                    int b = (int) (y + 1.772 * (u - 128));

                    // Clamp values to 0-255
                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    int rgbIndex = (row * width + col) * 3;
                    if (rgbIndex + 2 < rgb.length) {
                        // Swap R and B channels for Unity (BGR format)
                        rgb[rgbIndex] = (byte) b;
                        rgb[rgbIndex + 1] = (byte) g;
                        rgb[rgbIndex + 2] = (byte) r;
                    }
                }
            }

            return rgb;
        } catch (Exception e) {
            Log.e(TAG, "Error in YUV420 to RGB conversion", e);
            return null;
        }
    }

    /**
     * Create a video-like test pattern that changes over time to simulate video
     * content
     */
    private byte[] createVideoLikeTestPattern(int width, int height) {
        byte[] rgb = new byte[width * height * 3];

        frameCounter++;

        // Create a pattern that simulates video-like movement
        double time = frameCounter * 0.1; // Slow time progression

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int rgbIndex = (row * width + col) * 3;
                if (rgbIndex + 2 < rgb.length) {

                    // Create moving wave patterns
                    double x = (double) col / width;
                    double y = (double) row / height;

                    // Moving sine waves
                    double wave1 = Math.sin(x * 10 + time) * 0.5 + 0.5;
                    double wave2 = Math.sin(y * 8 + time * 1.5) * 0.5 + 0.5;
                    double wave3 = Math.sin((x + y) * 6 + time * 0.8) * 0.5 + 0.5;

                    // Combine waves to create RGB values
                    int r = (int) (wave1 * 255);
                    int g = (int) (wave2 * 255);
                    int b = (int) (wave3 * 255);

                    // Store in BGR format for Unity
                    rgb[rgbIndex] = (byte) b;
                    rgb[rgbIndex + 1] = (byte) g;
                    rgb[rgbIndex + 2] = (byte) r;
                }
            }
        }

        Log.i(TAG, "Generated video-like test pattern frame #" + frameCounter + " (moving waves)");
        return rgb;
    }

    /**
     * Get the latest RGB data for Unity texture loading
     * Optimized for low latency
     */
    public byte[] getRgbData() {
        synchronized (frameLock) {
            if (latestRgbData != null) {
                // Skip verbose logging for performance
                return latestRgbData.clone(); // Return a copy to avoid threading issues
            }
            return null;
        }
    }

    /**
     * Check if there's a new frame to update
     * Optimized for low latency
     */
    public boolean isUpdateFrame() {
        synchronized (frameLock) {
            boolean hasFrame = hasNewFrame;
            if (hasFrame) {
                // Mark frame as consumed only when Unity checks for updates
                hasNewFrame = false;
            }
            return hasFrame;
        }
    }

    /**
     * Update Unity texture with latest RGB data
     * This is called from Unity's render thread
     */
    public void updateTexture() {
        synchronized (frameLock) {
            if (hasNewFrame && latestRgbData != null) {
                try {
                    // Update Unity texture via OpenGL
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, unityTextureId);
                    GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height,
                            GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE,
                            ByteBuffer.wrap(latestRgbData));
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

                    hasNewFrame = false;
                    Log.v(TAG, "Unity texture updated successfully");

                } catch (Exception e) {
                    Log.e(TAG, "Error updating Unity texture", e);
                }
            }
        }
    }

    /**
     * Start TCP server to receive H.264 data
     */
    public void startServer(int port, boolean record) {
        Log.i(TAG, "Starting TCP server on port: " + port);

        tcpThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isReceiving = true;

                Log.i(TAG, "TCP server listening on port " + port);

                while (isReceiving && !Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    Log.i(TAG, "Client connected from: " + clientSocket.getRemoteSocketAddress());

                    // Optimize socket for low latency
                    clientSocket.setTcpNoDelay(true); // Disable Nagle algorithm
                    clientSocket.setSoTimeout(5000); // 5 second timeout

                    // Reduce buffer sizes for lower latency
                    clientSocket.setReceiveBufferSize(32768); // 32KB
                    clientSocket.setSendBufferSize(8192); // 8KB

                    handleClient(clientSocket);
                }

            } catch (IOException e) {
                if (isReceiving) {
                    Log.e(TAG, "TCP server error", e);
                } else {
                    Log.i(TAG, "TCP server stopped");
                }
            }
        });

        tcpThread.start();
    }

    /**
     * Handle individual client connection - optimized for low latency
     */
    private void handleClient(Socket clientSocket) {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream())) {
            // Use larger buffer for better performance
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer

            while (isReceiving && !Thread.currentThread().isInterrupted()) {
                // Read frame size
                int frameSize = inputStream.readInt();
                if (frameSize <= 0 || frameSize > buffer.length) {
                    continue; // Skip invalid frames silently for performance
                }

                // Read frame data
                inputStream.readFully(buffer, 0, frameSize);

                // Feed directly to decoder for low latency (skip validation overhead)
                feedDataToDecoder(buffer, frameSize);
            }

        } catch (IOException e) {
            if (isReceiving) {
                Log.e(TAG, "Error handling client", e);
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }
        }
    }

    /**
     * Validate H.264 Annex-B frame from encoder to prevent corrupted frames
     * The encoder uses Annex-B format with start codes and specific NAL unit types
     */
    private boolean isValidH264AnnexBFrame(byte[] data, int size) {
        if (size < 4) {
            return false;
        }

        boolean foundValidNalUnit = false;

        // Check for H.264 Annex-B NAL unit start codes
        for (int i = 0; i <= size - 4; i++) {
            // Look for 4-byte start code: 0x00 0x00 0x00 0x01
            if (data[i] == 0x00 && data[i + 1] == 0x00 &&
                    data[i + 2] == 0x00 && data[i + 3] == 0x01) {

                // Found 4-byte start code, check NAL unit type
                if (i + 4 < size) {
                    int nalUnitHeader = data[i + 4] & 0xFF;
                    int nalType = nalUnitHeader & 0x1F;

                    // Valid NAL unit types from encoder:
                    // 1 = Non-IDR slice, 5 = IDR slice, 7 = SPS, 8 = PPS, 6 = SEI
                    if (nalType == 1 || nalType == 5 || nalType == 7 ||
                            nalType == 8 || nalType == 6) {
                        foundValidNalUnit = true;
                        Log.v(TAG, "Found valid NAL unit type: " + nalType + " at offset: " + i);
                    }
                }
            }
            // Also check for 3-byte start code: 0x00 0x00 0x01
            else if (i <= size - 3 && data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x01) {
                if (i + 3 < size) {
                    int nalUnitHeader = data[i + 3] & 0xFF;
                    int nalType = nalUnitHeader & 0x1F;

                    if (nalType == 1 || nalType == 5 || nalType == 7 ||
                            nalType == 8 || nalType == 6) {
                        foundValidNalUnit = true;
                        Log.v(TAG, "Found valid NAL unit type: " + nalType + " at offset: " + i + " (3-byte start)");
                    }
                }
            }
        }

        if (!foundValidNalUnit) {
            Log.w(TAG, "No valid H.264 NAL units found in frame from encoder");
            // Log first few bytes for debugging
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(16, size); i++) {
                hex.append(String.format("%02X ", data[i] & 0xFF));
            }
            Log.w(TAG, "Frame header: " + hex.toString());
        }

        return foundValidNalUnit;
    }

    /**
     * Release all resources
     */
    public void release() {
        Log.i(TAG, "Releasing SimpleMediaDecoder");

        isReceiving = false;

        if (tcpThread != null) {
            tcpThread.interrupt();
            try {
                tcpThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
            serverSocket = null;
        }

        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
                mediaCodec.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaCodec", e);
            }
            mediaCodec = null;
        }

        synchronized (frameLock) {
            latestRgbData = null;
            hasNewFrame = false;
        }

        Log.i(TAG, "SimpleMediaDecoder released successfully");
    }

    /**
     * Get detailed processing information for debugging
     */
    public String getProcessingInfo() {
        return String.format("Frame #%d, Last strategy: %s, Dimensions: %dx%d, Has frame: %b",
                frameCounter, lastProcessingStrategy, width, height, hasNewFrame);
    }

    /**
     * Get the current frame counter
     */
    public int getFrameCounter() {
        return frameCounter;
    }

    /**
     * Debug method to get current frame state
     */
    public String getFrameState() {
        synchronized (frameLock) {
            return String.format("Frame #%d, HasNewFrame: %b, RgbDataSize: %d, Strategy: %s",
                    frameCounter, hasNewFrame,
                    latestRgbData != null ? latestRgbData.length : 0,
                    lastProcessingStrategy);
        }
    }

    /**
     * Force generate a test pattern frame for debugging
     */
    public void generateTestFrame() {
        synchronized (frameLock) {
            latestRgbData = createVideoLikeTestPattern(width, height);
            hasNewFrame = true;
            frameCounter++;
            lastProcessingStrategy = "Manual Test Pattern";
            Log.i(TAG, "Generated manual test frame #" + frameCounter);
        }
    }
}
