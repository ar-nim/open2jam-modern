package org.open2jam.parsers.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A class for help with ZLIB compression/decompression
 *
 * @author CdK
 */
public class Compressor {

    /** Maximum allowed decompressed size (50MB) to prevent zip bomb attacks */
    private static final int MAX_DECOMPRESSED_SIZE = 50 * 1024 * 1024;

    /**
     * ZLIB inflate (decompress) of a ByteBuffer
     * @param in The byteBuffer
     * @return A ByteBuffer with the later decompressed
     */
    public static ByteBuffer decompress(ByteBuffer in)
    {
        //bytebuffer to byte[]
        byte[] bin = new byte[in.remaining()];
        in.get(bin);

        Inflater decompressor = new Inflater();
        decompressor.setInput(bin);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(bin.length);

        byte[] buf = new byte[1024];
        long totalOutput = 0;

        while(true)
        {
            try {
                int count = decompressor.inflate(buf);
                if(count == 0 && decompressor.finished()) break;
                totalOutput += count;
                if (totalOutput > MAX_DECOMPRESSED_SIZE) {
                    Logger.global.log(Level.WARNING, "Decompressed data exceeds maximum allowed size ({0} bytes)", MAX_DECOMPRESSED_SIZE);
                    decompressor.end();
                    return null;
                }
                bos.write(buf, 0, count);
            } catch (DataFormatException ex) {
                Logger.global.log(Level.WARNING, "FUUUUU decompress FAILED! D:");
                decompressor.end();
                return null;
            }
        }

        decompressor.end();
	in.clear();
        try {
            bos.close();
        } catch (IOException e) {
            Logger.global.log(Level.WARNING, "Are you fucking kidding me?");
            return null;
        }

        return ByteBuffer.wrap(bos.toByteArray());
    }
}
