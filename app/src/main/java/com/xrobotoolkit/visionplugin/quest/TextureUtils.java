package com.xrobotoolkit.visionplugin.quest;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES30;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for Unity texture format conversions and processing
 */
public class TextureUtils {
    private static final String TAG = "TextureUtils";
    
    // Unity texture format constants
    public static final int UNITY_FORMAT_RGBA32 = GLES30.GL_RGBA;
    public static final int UNITY_FORMAT_RGB24 = GLES30.GL_RGB;
    public static final int UNITY_FORMAT_ALPHA8 = GLES30.GL_ALPHA;
    public static final int UNITY_FORMAT_ARGB32 = 1000; // Custom format identifier
    
    /**
     * Convert Unity ARGB32 format to RGBA format for OpenGL
     */
    public static byte[] convertARGBtoRGBA(byte[] argbData) {
        if (argbData == null || argbData.length % 4 != 0) {
            Log.e(TAG, "Invalid ARGB data");
            return null;
        }
        
        byte[] rgbaData = new byte[argbData.length];
        
        for (int i = 0; i < argbData.length; i += 4) {
            // ARGB -> RGBA conversion
            byte a = argbData[i];     // Alpha
            byte r = argbData[i + 1]; // Red
            byte g = argbData[i + 2]; // Green
            byte b = argbData[i + 3]; // Blue
            
            rgbaData[i] = r;         // Red
            rgbaData[i + 1] = g;     // Green
            rgbaData[i + 2] = b;     // Blue
            rgbaData[i + 3] = a;     // Alpha
        }
        
        return rgbaData;
    }
    
    /**
     * Convert Bitmap to byte array in RGBA format
     */
    public static byte[] bitmapToRGBABytes(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Cannot convert null bitmap");
            return null;
        }
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4);
        buffer.order(ByteOrder.nativeOrder());
        
        for (int pixel : pixels) {
            buffer.put((byte) Color.red(pixel));   // Red
            buffer.put((byte) Color.green(pixel)); // Green
            buffer.put((byte) Color.blue(pixel));  // Blue
            buffer.put((byte) Color.alpha(pixel)); // Alpha
        }
        
        return buffer.array();
    }
    
    /**
     * Create bitmap from RGBA byte array
     */
    public static Bitmap createBitmapFromRGBA(byte[] rgbaData, int width, int height) {
        if (rgbaData == null || rgbaData.length != width * height * 4) {
            Log.e(TAG, "Invalid RGBA data or dimensions");
            return null;
        }
        
        int[] pixels = new int[width * height];
        
        for (int i = 0; i < pixels.length; i++) {
            int r = rgbaData[i * 4] & 0xFF;
            int g = rgbaData[i * 4 + 1] & 0xFF;
            int b = rgbaData[i * 4 + 2] & 0xFF;
            int a = rgbaData[i * 4 + 3] & 0xFF;
            
            pixels[i] = Color.argb(a, r, g, b);
        }
        
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }
    
    /**
     * Flip texture vertically (Unity textures are often flipped)
     */
    public static byte[] flipTextureVertically(byte[] textureData, int width, int height, int bytesPerPixel) {
        if (textureData == null) {
            Log.e(TAG, "Cannot flip null texture data");
            return null;
        }
        
        byte[] flippedData = new byte[textureData.length];
        int rowSize = width * bytesPerPixel;
        
        for (int y = 0; y < height; y++) {
            int srcOffset = y * rowSize;
            int dstOffset = (height - 1 - y) * rowSize;
            System.arraycopy(textureData, srcOffset, flippedData, dstOffset, rowSize);
        }
        
        return flippedData;
    }
    
    /**
     * Resize texture data using nearest neighbor interpolation
     */
    public static byte[] resizeTexture(byte[] textureData, int oldWidth, int oldHeight, 
                                      int newWidth, int newHeight, int bytesPerPixel) {
        if (textureData == null) {
            Log.e(TAG, "Cannot resize null texture data");
            return null;
        }
        
        byte[] resizedData = new byte[newWidth * newHeight * bytesPerPixel];
        
        float xRatio = (float) oldWidth / newWidth;
        float yRatio = (float) oldHeight / newHeight;
        
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int srcX = (int) (x * xRatio);
                int srcY = (int) (y * yRatio);
                
                // Ensure bounds
                srcX = Math.min(srcX, oldWidth - 1);
                srcY = Math.min(srcY, oldHeight - 1);
                
                int srcOffset = (srcY * oldWidth + srcX) * bytesPerPixel;
                int dstOffset = (y * newWidth + x) * bytesPerPixel;
                
                System.arraycopy(textureData, srcOffset, resizedData, dstOffset, bytesPerPixel);
            }
        }
        
        return resizedData;
    }
    
    /**
     * Apply alpha mask to texture
     */
    public static byte[] applyAlphaMask(byte[] rgbaData, float alpha) {
        if (rgbaData == null || rgbaData.length % 4 != 0) {
            Log.e(TAG, "Invalid RGBA data for alpha mask");
            return null;
        }
        
        byte[] maskedData = rgbaData.clone();
        int alphaValue = (int) (alpha * 255);
        
        for (int i = 3; i < maskedData.length; i += 4) {
            int currentAlpha = maskedData[i] & 0xFF;
            maskedData[i] = (byte) ((currentAlpha * alphaValue) / 255);
        }
        
        return maskedData;
    }
    
    /**
     * Convert OpenGL format constant to string for debugging
     */
    public static String formatToString(int format) {
        switch (format) {
            case GLES30.GL_RGBA:
                return "GL_RGBA";
            case GLES30.GL_RGB:
                return "GL_RGB";
            case GLES30.GL_ALPHA:
                return "GL_ALPHA";
            case GLES30.GL_LUMINANCE:
                return "GL_LUMINANCE";
            case GLES30.GL_LUMINANCE_ALPHA:
                return "GL_LUMINANCE_ALPHA";
            case UNITY_FORMAT_ARGB32:
                return "UNITY_ARGB32";
            default:
                return "UNKNOWN_FORMAT(" + format + ")";
        }
    }
    
    /**
     * Get bytes per pixel for a given format
     */
    public static int getBytesPerPixel(int format) {
        switch (format) {
            case GLES30.GL_RGBA:
            case UNITY_FORMAT_ARGB32:
                return 4;
            case GLES30.GL_RGB:
                return 3;
            case GLES30.GL_LUMINANCE_ALPHA:
                return 2;
            case GLES30.GL_ALPHA:
            case GLES30.GL_LUMINANCE:
                return 1;
            default:
                Log.w(TAG, "Unknown format: " + format + ", assuming 4 bytes per pixel");
                return 4;
        }
    }
    
    /**
     * Validate texture dimensions
     */
    public static boolean isValidTextureDimension(int dimension) {
        return dimension > 0 && dimension <= 4096 && isPowerOfTwo(dimension);
    }
    
    /**
     * Check if a number is power of two (recommended for OpenGL textures)
     */
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Get next power of two for a given number
     */
    public static int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        if (isPowerOfTwo(n)) return n;
        
        int power = 1;
        while (power < n) {
            power <<= 1;
        }
        return power;
    }
    
    /**
     * Log texture information for debugging
     */
    public static void logTextureInfo(String tag, int width, int height, int format, int dataLength) {
        int expectedLength = width * height * getBytesPerPixel(format);
        String formatStr = formatToString(format);
        
        Log.i(TAG, tag + " - Texture Info:");
        Log.i(TAG, "  Dimensions: " + width + "x" + height);
        Log.i(TAG, "  Format: " + formatStr);
        Log.i(TAG, "  Data length: " + dataLength + " bytes");
        Log.i(TAG, "  Expected length: " + expectedLength + " bytes");
        Log.i(TAG, "  Valid: " + (dataLength == expectedLength));
    }
}
