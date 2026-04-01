package org.open2jam.parsers.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class OJNDecryptor {

    private static final byte[] ENCRYPTED_SIGNATURE = { 0x6E, 0x65, 0x77 }; // "new"
    private static final int HEADER_SIZE = 8;

    private OJNDecryptor() {
    }

    public static boolean isEncrypted(ByteBuffer buffer) {
        if (buffer.remaining() < 3) {
            return false;
        }
        buffer.mark();
        boolean match = buffer.get() == ENCRYPTED_SIGNATURE[0]
                     && buffer.get() == ENCRYPTED_SIGNATURE[1]
                     && buffer.get() == ENCRYPTED_SIGNATURE[2];
        buffer.reset();
        return match;
    }

    public static boolean isEncrypted(File file) throws IOException {
        if (file.length() < HEADER_SIZE) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] sig = new byte[3];
            raf.readFully(sig);
            return Arrays.equals(sig, ENCRYPTED_SIGNATURE);
        }
    }

    public static ByteBuffer decrypt(ByteBuffer data) {
        if (data.remaining() <= HEADER_SIZE) {
            throw new IllegalArgumentException("Encrypted OJN data too short: " + data.remaining() + " bytes");
        }

        data.order(ByteOrder.LITTLE_ENDIAN);

        // Skip 3-byte signature
        data.position(data.position() + 3);

        int blockSize = data.get() & 0xFF;
        int mainKey = data.get() & 0xFF;
        int midKey = data.get() & 0xFF;
        int initialKey = data.get() & 0xFF;
        // skip 1 byte padding (position is now at 8)
        data.get();

        if (blockSize == 0) {
            throw new IllegalArgumentException("Encrypted OJN blockSize cannot be 0");
        }

        // Build XOR key array
        byte[] encryptKey = new byte[blockSize];
        Arrays.fill(encryptKey, (byte) mainKey);
        encryptKey[0] = (byte) initialKey;
        encryptKey[blockSize / 2] = (byte) midKey;

        // Decrypt: read source bytes in reverse order, XOR with key blocks
        int dataLength = data.limit();
        int outputLength = dataLength - HEADER_SIZE;
        byte[] output = new byte[outputLength];

        for (int i = 0; i < outputLength; i += blockSize) {
            for (int j = 0; j < blockSize; j++) {
                int offset = i + j;
                if (offset >= outputLength) {
                    break;
                }
                output[offset] = (byte) (data.get(dataLength - (offset + 1)) ^ encryptKey[j]);
            }
        }

        ByteBuffer result = ByteBuffer.wrap(output);
        result.order(ByteOrder.LITTLE_ENDIAN);
        return result;
    }
}
