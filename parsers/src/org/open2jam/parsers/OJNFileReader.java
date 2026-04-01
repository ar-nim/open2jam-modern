package org.open2jam.parsers;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import org.open2jam.parsers.utils.OJNDecryptor;

public class OJNFileReader {

    private OJNFileReader() {
    }

    public static ByteBuffer read(File file) throws IOException {
        if (OJNDecryptor.isEncrypted(file)) {
            return readEncryptedBuffer(file);
        }
        return readBuffer(file);
    }

    public static ByteBuffer read(File file, int offset, int length) throws IOException {
        if (OJNDecryptor.isEncrypted(file)) {
            ByteBuffer full = readEncryptedBuffer(file);
            full.position(offset);
            ByteBuffer slice = full.slice();
            slice.limit(Math.min(length, slice.capacity()));
            slice.order(ByteOrder.LITTLE_ENDIAN);
            return slice;
        }
        return readBufferRange(file, offset, length);
    }

    private static ByteBuffer readEncryptedBuffer(File file) throws IOException {
        try (RandomAccessFileChannelWrapper fileWrapper = new RandomAccessFileChannelWrapper(file)) {
            ByteBuffer raw = fileWrapper.map(0, fileWrapper.size());
            return OJNDecryptor.decrypt(raw);
        }
    }

    private static ByteBuffer readBuffer(File file) throws IOException {
        try (RandomAccessFileChannelWrapper fileWrapper = new RandomAccessFileChannelWrapper(file)) {
            ByteBuffer buffer = fileWrapper.map(0, fileWrapper.size());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer;
        }
    }

    private static ByteBuffer readBufferRange(File file, int offset, int length) throws IOException {
        try (RandomAccessFileChannelWrapper wrapper = new RandomAccessFileChannelWrapper(file)) {
            ByteBuffer buffer = wrapper.map(offset, length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer;
        }
    }

    /**
     * Wrapper class for RandomAccessFile to enable try-with-resources.
     */
    private static class RandomAccessFileChannelWrapper implements AutoCloseable {
        private final RandomAccessFile file;
        private final FileChannel channel;

        RandomAccessFileChannelWrapper(File file) throws IOException {
            this.file = new RandomAccessFile(file.getAbsolutePath(), "r");
            this.channel = this.file.getChannel();
        }

        ByteBuffer map(long start, long length) throws IOException {
            return channel.map(FileChannel.MapMode.READ_ONLY, start, length);
        }

        long size() throws IOException {
            return channel.size();
        }

        @Override
        public void close() throws IOException {
            channel.close();
            file.close();
        }
    }
}