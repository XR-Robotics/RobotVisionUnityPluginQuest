package com.xrobotoolkit.visionplugin.quest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MediaDecoder - Hardware-Accelerated Video Decoder with Network Integration
 * 
 * This class provides a comprehensive video decoding solution that combines:
 * - Hardware-accelerated H.264 video decoding using Android MediaCodec
 * - Real-time network streaming via TCP with length-prefixed protocol
 * - Direct Surface rendering for optimal performance
 * - Low-latency configuration for real-time applications
 * - Optional video recording and frame export capabilities
 * 
 * Architecture Overview:
 * - Uses MediaCodec for hardware H.264 decoding
 * - Integrates with FBOPlugin for Unity texture rendering (optional)
 * - Supports both FBO-based and direct Surface rendering modes
 * - TCP server handles length-prefixed video stream protocol
 * - Multi-threaded design for optimal performance
 * 
 * Key Features:
 * - Low-latency mode for real-time applications
 * - Automatic color space handling (sRGB support)
 * - Robust error handling and recovery
 * - Frame timing and presentation timestamp management
 * - Optional PNG frame export for debugging
 * - Video stream recording capabilities
 * - Thread-safe operations with proper cleanup
 * 
 * Usage Patterns:
 * 1. Standalone Mode: initialize() + startServer() for FBO-based rendering
 * 2. Integrated Mode: initializeWithSurface() + startServer() for direct
 * Surface rendering
 * 
 * Threading Model:
 * - TCP server runs on background thread for network I/O
 * - MediaCodec operates on internal decoder threads
 * - Frame callbacks synchronized with Unity render thread
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class MediaDecoder {
    private final String TAG = "MediaDecoder";

    // Core MediaCodec components
    private MediaCodec mediaCodec; // Hardware decoder instance
    private String mimeType = "video/avc"; // H.264/AVC MIME type

    // Unity integration components (for standalone mode)
    private int previewTextureId; // Unity texture ID
    private FBOPlugin mFBOPlugin = null; // FBO-based rendering pipeline

    // Network streaming components
    private Thread tcpThread; // Background thread for TCP server
    private volatile boolean receivie = false; // Thread-safe flag for server state

    // Video configuration
    private int width; // Video frame width
    private int height; // Video frame height

    // Frame timing management
    private long lastTimestamp = 0; // Last frame presentation timestamp

    /**
     * Initializes the MediaDecoder with FBO-based rendering pipeline.
     * This mode uses FBOPlugin to copy decoded frames to Unity textures.
     * 
     * Features:
     * - Hardware-accelerated H.264 decoding
     * - Low-latency mode for real-time applications
     * - sRGB color space configuration
     * - FBO-based texture copying to Unity
     * - Automatic error handling and cleanup
     * 
     * @param unityTextureId Unity texture ID to render decoded frames
     * @param width          Video frame width in pixels
     * @param height         Video frame height in pixels
     * @throws Exception if initialization fails
     */
    public void initialize(int unityTextureId, int width, int height) throws Exception {
        Log.i(TAG, "initialize: textureId=" + unityTextureId + ", size=" + width + "x" + height);

        // Clean up any existing resources first to prevent resource leaks
        if (mediaCodec != null) {
            Log.i(TAG, "Cleaning up existing MediaCodec before reinitializing");
            release();
            // Add a small delay to ensure proper cleanup
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Store configuration parameters
        this.width = width;
        this.height = height;
        this.previewTextureId = unityTextureId;

        // Initialize FBOPlugin for Unity texture rendering
        if (mFBOPlugin == null) {
            mFBOPlugin = new FBOPlugin();
        }
        mFBOPlugin.init();
        mFBOPlugin.BuildTexture(unityTextureId, width, height);

        // Create and configure MediaCodec with comprehensive error handling
        try {
            // Create video format for H.264/AVC with specified dimensions
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

            // Configure low-latency mode for real-time applications
            format.setInteger(MediaFormat.KEY_PRIORITY, 0); // Real-time priority
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1); // Enable low latency mode

            // Attempt to configure sRGB color space if supported by device
            try {
                format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
                format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
                format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
                Log.i(TAG, "MediaFormat configured for sRGB color space");
            } catch (Exception colorEx) {
                Log.w(TAG, "Could not configure sRGB color space in MediaFormat, will handle in shader");
            }

            // Create decoder instance
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            if (mediaCodec == null) {
                throw new Exception("Failed to create MediaCodec decoder");
            }

            // Configure decoder with FBO surface and start
            mediaCodec.configure(format, mFBOPlugin.getSurface(), null, 0);
            mediaCodec.start();

            Log.i(TAG, "MediaCodec created and started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaCodec", e);
            if (mediaCodec != null) {
                try {
                    mediaCodec.release();
                } catch (Exception releaseEx) {
                    Log.e(TAG, "Error releasing failed MediaCodec", releaseEx);
                }
                mediaCodec = null;
            }
            throw e;
        }

        // Reset state variables
        lastTimestamp = 0;
        savePng = false;

        Log.i(TAG, "MediaDecoder initialized successfully with low latency mode enabled");
    }

    /**
     * Initializes the MediaDecoder with direct Surface rendering.
     * This mode renders decoded frames directly to the provided surface,
     * bypassing FBO operations for optimal performance.
     * 
     * Features:
     * - Direct Surface rendering (no texture copying)
     * - Hardware-accelerated H.264 decoding
     * - Low-latency mode configuration
     * - sRGB color space support
     * - Optimal performance for integrated rendering
     * 
     * @param surface External surface to render decoded frames
     * @param width   Video frame width in pixels
     * @param height  Video frame height in pixels
     * @throws Exception if initialization fails
     */
    public void initializeWithSurface(Surface surface, int width, int height) throws Exception {
        Log.i(TAG, "initializeWithSurface: surface=" + surface + ", size=" + width + "x" + height);

        // Clean up any existing resources first
        if (mediaCodec != null) {
            Log.i(TAG, "Cleaning up existing MediaCodec before reinitializing");
            release();
            // Add a small delay to ensure proper cleanup
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        this.width = width;
        this.height = height;

        // Create and configure MediaCodec with provided surface
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

            // Enable low latency mode
            format.setInteger(MediaFormat.KEY_PRIORITY, 0); // Real-time priority
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1); // Enable low latency mode

            // Attempt to configure for sRGB color space if supported
            try {
                format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709);
                format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
                format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
                Log.i(TAG, "MediaFormat configured for sRGB color space (with surface)");
            } catch (Exception colorEx) {
                Log.w(TAG, "Could not configure sRGB color space in MediaFormat, will handle in shader");
            }

            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            if (mediaCodec == null) {
                throw new Exception("Failed to create MediaCodec decoder");
            }

            // Use the provided surface directly
            mediaCodec.configure(format, surface, null, 0);
            mediaCodec.start();

            Log.i(TAG, "MediaCodec created and started successfully with external surface");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaCodec with surface", e);
            if (mediaCodec != null) {
                try {
                    mediaCodec.release();
                } catch (Exception releaseEx) {
                    Log.e(TAG, "Error releasing failed MediaCodec", releaseEx);
                }
                mediaCodec = null;
            }
            throw e;
        }

        // Reset state variables
        lastTimestamp = 0;
        savePng = false;

        Log.i(TAG, "MediaDecoder initialized successfully with external surface");
    }

    // Data buffer for network operations (reusable to reduce GC pressure)
    private byte[] buffer;

    /**
     * Starts the TCP server to receive H.264 video stream data.
     * Creates a background thread to handle client connections and data reception.
     * Implements length-prefixed protocol for reliable message framing.
     * 
     * Protocol Format:
     * - 4-byte big-endian length header
     * - Variable-length H.264 NAL unit data
     * - Continuous stream of encoded frames
     * 
     * @param port   TCP port to listen on for incoming connections
     * @param record Whether to record received data to file for debugging
     * @throws IOException if server socket creation fails
     */
    public void startServer(int port, boolean record) throws IOException {
        // Stop existing TCP server if running to prevent conflicts
        stopServer();

        receivie = true;
        Log.i(TAG, "Starting TCP server on port: " + port);

        // Initialize reusable buffer to reduce memory allocations
        if (buffer == null) {
            buffer = new byte[1024 * 1024]; // 1MB buffer for video data
        }

        tcpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket serverSocket = null;
                Socket socket = null;
                InputStream inputStream = null;
                DataInputStream dataInputStream = null;

                try {
                    Log.i(TAG, "-ServerSocket:" + port);
                    serverSocket = new ServerSocket(port);
                    socket = serverSocket.accept();
                    Log.i(TAG, "Socket connected:" + port);
                    inputStream = socket.getInputStream();
                    dataInputStream = new DataInputStream(inputStream);

                    while (receivie && !Thread.currentThread().isInterrupted()) {
                        try {
                            // Read the 4-byte header and obtain the length of the packet body
                            byte[] header = new byte[4];
                            dataInputStream.readFully(header);

                            // Analyze package length (big end mode)
                            ByteBuffer buffer = ByteBuffer.wrap(header);
                            buffer.order(ByteOrder.BIG_ENDIAN);
                            int bodyLength = buffer.getInt();
                            // Log.i(TAG, "Received packet length: " + bodyLength);

                            byte[] body = new byte[bodyLength];
                            dataInputStream.readFully(body); // Ensure complete reading

                            if (!receivie || Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            // Log.i(TAG, "inputStream.read: " + bodyLength);
                            if (bodyLength > 0) {
                                if (record) {
                                    Record(body, bodyLength);
                                }
                                if (isMediaCodecReady()) {
                                    decode(body, bodyLength);
                                } else {
                                    Log.w(TAG, "MediaCodec not ready, skipping decode");
                                }
                            }
                        } catch (IOException e) {
                            if (receivie && !Thread.currentThread().isInterrupted()) {
                                Log.e(TAG, "Error reading from socket", e);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (receivie && !Thread.currentThread().isInterrupted()) {
                        Log.e(TAG, "TCP server error", e);
                    }
                } finally {
                    // Clean up resources
                    try {
                        if (dataInputStream != null)
                            dataInputStream.close();
                        if (inputStream != null)
                            inputStream.close();
                        if (socket != null)
                            socket.close();
                        if (serverSocket != null)
                            serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing TCP resources", e);
                    }
                    Log.i(TAG, "TCP thread finished");
                }
            }
        });
        tcpThread.start();
    }

    FileOutputStream fileOutputStream = null;

    private void Record(byte[] data, int length) throws Exception {
        try {
            if (fileOutputStream == null) {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "/sdcard/Download/received_data_" + timeStamp + ".bin";
                Log.i(TAG, "fileName:" + fileName);
                File file = new File(fileName);
                fileOutputStream = new FileOutputStream(file);
            }
            fileOutputStream.write(data, 0, length);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isUpdateFrame() {
        if (mFBOPlugin != null) {
            return mFBOPlugin.isUpdateFrame();
        }
        // When using external surface (integrated approach), frames are handled
        // automatically
        return false;
    }

    public void updateTexture() {
        // When using FBOPlugin (standalone approach)
        if (mFBOPlugin != null && isUpdateFrame()) {
            mFBOPlugin.updateTexture();
        }
        // When using external surface (integrated approach),
        // texture updates are handled by the SurfaceTexture's onFrameAvailable callback
    }

    private long computePresentationTime(long frameIndex) {

        // return 132 + frameIndex * 1000000 / 30;
        long currentTime = System.nanoTime() / 1000; // Convert to microseconds
        lastTimestamp = Math.max(lastTimestamp + 1, currentTime);
        return lastTimestamp;

    }

    private void decode(byte[] data, int length) throws Exception {
        if (mediaCodec == null) {
            Log.e(TAG, "MediaCodec is null, cannot decode");
            return;
        }

        // Log.i(TAG, "decode length:" + length + " isComplete:" + isComplete + "
        // isKeyFrame:" + isKeyFrame + " nalu:" + nalu);
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(data, 0, length);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, computePresentationTime(inputBufferIndex), 0);
            } else {
                Log.w(TAG, "Input buffer is null for index: " + inputBufferIndex);
                return;
            }
        } else {
            Log.w(TAG, "No input buffer available: " + inputBufferIndex);
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10);
        Log.i(TAG, "outputBufferIndex:" + outputBufferIndex + " savePng:" + savePng);

        int loopCount = 0;
        while (true) {
            loopCount++;
            if (loopCount > 10) { // Prevent infinite loops
                Log.w(TAG, "Breaking decode loop after 10 iterations");
                break;
            }

            if (!receivie) {
                break;
            }

            if (outputBufferIndex >= 0) {
                if (savePng) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    Log.i(TAG, "saveFrameAsJPEG:" + outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        Log.i(TAG, "saveFrameAs size:" + bufferInfo.size);

                        byte[] yuvData = new byte[bufferInfo.size];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        outputBuffer.get(yuvData);

                        saveBitmapFromYUV(yuvData, width, height);
                    }
                    savePng = false;
                }

                // Release output buffer
                mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);

            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                // Update renderer configuration
                Log.i(TAG, "reset mediaFormat " + newFormat);
                if (newFormat.containsKey("csd-0") && newFormat.containsKey("csd-1")) {
                    Log.i(TAG, "newFormat containsKey " + newFormat);
                }
                break;
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.i(TAG, "Output buffers changed");
                break;
            } else {
                Log.w(TAG, "Unexpected output buffer index: " + outputBufferIndex);
                break;
            }
        }
    }

    private void saveBitmapFromYUV(byte[] yuvData, int width, int height) {
        Bitmap bitmap = yuvToBitmap(yuvData, width, height);
        if (bitmap != null) {
            try {
                File file = new File("/sdcard/Download/", "decoded_frame_" + System.currentTimeMillis() + ".png");
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                Log.i(TAG, "Saved PNG frame to " + file.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Failed to save PNG", e);
            }
        }
    }

    /**
     * YUV420 -> Bitmap
     */
    private Bitmap yuvToBitmap(byte[] yuvData, int width, int height) {
        YuvImage yuvImage = new YuvImage(yuvData, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public void release() throws IOException {
        Log.i(TAG, "mediaCodec release");

        // Stop TCP server first
        stopServer();

        // Stop and release MediaCodec with proper state checking
        if (mediaCodec != null) {
            try {
                // Flush any pending operations
                mediaCodec.flush();
                Log.i(TAG, "MediaCodec flushed");

                // Stop the codec
                mediaCodec.stop();
                Log.i(TAG, "MediaCodec stopped");

                // Release the codec
                mediaCodec.release();
                Log.i(TAG, "MediaCodec released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaCodec", e);
            } finally {
                mediaCodec = null;
            }
        }

        // Clean up FBOPlugin
        if (mFBOPlugin != null) {
            try {
                mFBOPlugin.release(); // Assume FBOPlugin has a release method
                Log.i(TAG, "FBOPlugin released");
            } catch (Exception e) {
                Log.w(TAG, "Error releasing FBOPlugin (method may not exist)", e);
            } finally {
                mFBOPlugin = null;
            }
        }

        // Reset state variables
        lastTimestamp = 0;
        savePng = false;
        previewTextureId = 0;
        width = 0;
        height = 0;
        buffer = null;

        Log.i(TAG, "MediaDecoder fully released and reset");
    }

    boolean savePng = false;

    public void SavePng() {
        savePng = true;
    }

    /**
     * Stop the TCP server and clean up resources
     */
    public void stopServer() {
        Log.i(TAG, "stopTCPServer");
        receivie = false;

        if (tcpThread != null) {
            tcpThread.interrupt(); // Interrupt the thread if it's blocking on I/O
            try {
                tcpThread.join(100); // Wait for the thread to die for up to 0.1 seconds
                if (tcpThread.isAlive()) {
                    Log.w(TAG, "TCP thread did not terminate in time. Forcing shutdown...");
                    // You might want to take additional action here, like closing sockets
                } else {
                    Log.i(TAG, "TCP thread stopped successfully.");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for TCP thread to stop.", e);
            }
        }

        // Close file output stream if open
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing fileOutputStream", e);
            } finally {
                fileOutputStream = null;
            }
        }
    }

    /**
     * Check if MediaCodec is in a valid state for decoding
     */
    private boolean isMediaCodecReady() {
        return mediaCodec != null;
    }
}
