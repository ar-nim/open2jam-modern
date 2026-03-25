package org.open2jam.parsers;

import org.open2jam.parsers.Chart;
import org.open2jam.parsers.Event;
import org.open2jam.parsers.EventList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for calculating SHA-256 identity hashes of chart note data.
 * 
 * <h2>Why SHA-256 of Note Data?</h2>
 * <ul>
 *   <li><strong>Filename changes don't affect identity:</strong> Hash remains the same if file is renamed</li>
 *   <li><strong>Note edits DO affect identity:</strong> Hash changes when notes are modified, resetting scores</li>
 *   <li><strong>Provides immutable fingerprint:</strong> Reliable key for score tracking across library moves</li>
 * </ul>
 * 
 * <h2>Hash Components</h2>
 * <p>The hash includes the following fields for each event:</p>
 * <ul>
 *   <li>Measure number</li>
 *   <li>Channel ordinal</li>
 *   <li>Position within measure</li>
 *   <li>Event value (note value, BPM, etc.)</li>
 *   <li>Event flag ordinal</li>
 * </ul>
 * <p>Volume and pan are NOT included - they're gameplay modifiers, not chart definition.</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. MessageDigest instances are created per-call.</p>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>SHA-256 is cryptographically secure - collision resistance is sufficient for score tracking</li>
 *   <li>No user input is hashed - only parsed chart data from trusted parsers</li>
 *   <li>Hash calculation is CPU-intensive but runs asynchronously (doesn't block UI)</li>
 * </ul>
 * 
 * @author open2jam-modern team
 */
public class SHA256Util {

    /**
     * Calculate SHA-256 hash of chart note data.
     * 
     * <p>Hash includes: measure, channel, position, value, flags for all events.
     * Events are hashed in iteration order (should be sorted by measure/position).</p>
     * 
     * <p>Memory Safety: Uses fixed-size ByteBuffer (32 bytes) per event, reused for each event.
     * No large allocations even for charts with 10,000+ notes.</p>
     * 
     * @param chart Chart to hash
     * @return 64-character hex string (32 bytes), or null if error during hashing
     * @throws RuntimeException if SHA-256 algorithm not available (should never happen per Java spec)
     */
    public static String hashChart(Chart chart) {
        if (chart == null) {
            return null;
        }

        try {
            EventList events = chart.getEvents();
            if (events == null || events.isEmpty()) {
                return null;
            }

            // SHA-256 is guaranteed to exist per Java spec
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            // Reuse single ByteBuffer for all events (zero-allocation loop)
            ByteBuffer buffer = ByteBuffer.allocate(32);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Hash each event in iteration order
            for (Event event : events) {
                // Clear buffer for this event
                buffer.clear();

                // Hash components that define this event's identity
                buffer.putInt(event.getMeasure());                    // 4 bytes
                buffer.putShort((short) event.getChannel().ordinal()); // 2 bytes
                buffer.putDouble(event.getPosition());                 // 8 bytes
                buffer.putInt((int) event.getValue());                 // 4 bytes
                buffer.put((byte) event.getFlag().ordinal());          // 1 byte
                // 15 bytes padding (buffer is 32 bytes total)

                // Feed to hash function
                buffer.flip();
                md.update(buffer.array(), 0, buffer.limit());
            }

            // Convert to hex string (64 characters)
            byte[] hashBytes = md.digest();
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist per Java spec
            throw new RuntimeException("SHA-256 algorithm not available", e);
        } catch (Exception e) {
            // Chart parsing error, null events, etc.
            return null;
        }
    }

    /**
     * Verify if a chart's hash matches the expected identity.
     * 
     * <p>Use case: Before saving a score, verify the chart hasn't been modified
     * since the cache was populated.</p>
     * 
     * @param chart Chart to verify
     * @param expectedHash Expected SHA-256 hash (64 hex characters)
     * @return true if hash matches (chart unchanged), false otherwise
     */
    public static boolean verifyChart(Chart chart, String expectedHash) {
        if (expectedHash == null || expectedHash.length() != 64) {
            return false;
        }
        String actualHash = hashChart(chart);
        if (actualHash == null) {
            return false;
        }
        // Constant-time comparison to prevent timing attacks (paranoid but safe)
        return constantTimeEquals(actualHash.getBytes(), expectedHash.getBytes());
    }

    /**
     * Convert byte array to hexadecimal string.
     * 
     * @param bytes Bytes to convert
     * @return Hex string (2 characters per byte, lowercase)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * <p>Security Note: While timing attacks on chart hashes are purely theoretical
     * (no secret data involved), we use constant-time comparison as a best practice.</p>
     * 
     * @param a First byte array
     * @param b Second byte array
     * @return true if arrays are identical length and content
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
