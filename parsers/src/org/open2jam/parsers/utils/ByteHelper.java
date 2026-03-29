package org.open2jam.parsers.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.sigpwned.chardet4j.Chardet;

/**
 *
 * @author CdK
 */
public class ByteHelper {

    /**
     * Convert a byte[] to a String.
     * Uses UTF-8 encoding for O2Jam files.
     * @param ch The byte[]
     * @return a nice String
     */
    public static String toString(byte[] ch)
    {
        int i = 0;
        while(i<ch.length && ch[i]!=0)i++; // find \0 terminator
        try {
            return new String(ch,0,i,"UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Logger.global.log(Level.WARNING, "UTF-8 encoding not supported !");
            return new String(ch,0,i);
        }
    }

    /**
     * Detect encoding and convert byte[] to String for O2Jam OJN files.
     * 
     * O2Jam Encoding Priority:
     * 1. EUC-KR (CP949) - 90%+ of O2Jam charts are Korean
     * 2. UTF-8 - Modern/international charts  
     * 3. chardet4j - For Japanese/Chinese edge cases
     * 4. UTF-8 fallback
     * 
     * @param ch The byte[] (C-style null-terminated string buffer)
     * @return Decoded String, or empty string if all methods fail
     */
    public static String toStringAutoDetect(byte[] ch)
    {
        // Find null terminator (C-style string handling)
        int i = 0;
        while (i < ch.length && ch[i] != 0) i++;

        if (i == 0) {
            return "";
        }

        // Extract only valid bytes (before null terminator)
        byte[] textBytes = new byte[i];
        System.arraycopy(ch, 0, textBytes, 0, i);

        // Step 1: EUC-KR FIRST (O2Jam is Korean)
        try {
            return new String(textBytes, 0, i, "EUC-KR");
        } catch (UnsupportedEncodingException e) {
            // EUC-KR not available, continue to fallback
        }

        // Step 2: UTF-8 validation
        try {
            String utf8String = new String(textBytes, StandardCharsets.UTF_8);
            if (utf8String.getBytes(StandardCharsets.UTF_8).length == i) {
                return utf8String;
            }
        } catch (Exception e) {
            // Not valid UTF-8
        }

        // Step 3: chardet4j detection (Japanese/Chinese fallback)
        try {
            String detected = Chardet.decode(textBytes, StandardCharsets.UTF_8);
            if (detected != null && !detected.isEmpty()) {
                return detected;
            }
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "chardet4j detection failed: " + e.getMessage());
        }

        // Step 4: Final fallback - UTF-8
        return new String(ch, 0, i, StandardCharsets.UTF_8);
    }

    public static void copyTo(InputStream in, OutputStream out) throws IOException {
	int len;
	byte[] buffer = new byte[1024]; // thread-local allocation
	while((len = in.read(buffer)) > 0)
	    out.write(buffer, 0, len);

	in.close();
	out.close();
    }
    
    public static byte[] intToByteArray(int i)
    {
	return new byte[] {
	    (byte) (i),
	    (byte) (i >> 8),
	    (byte) (i >> 16),
	    (byte) (i >>> 24)
	};
    }
    
    public static byte[] shortToByteArray(short i)
    {
	return new byte[] {
	    (byte) (i),
	    (byte) (i >> 8)
	};
    }    
    
}
