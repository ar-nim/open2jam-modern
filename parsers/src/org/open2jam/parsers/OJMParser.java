package org.open2jam.parsers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.open2jam.parsers.utils.ByteBufferInputStream;
import org.open2jam.parsers.utils.ByteHelper;
import org.open2jam.parsers.utils.Logger;
import org.open2jam.parsers.utils.SampleData;


/**
 * Parser for OJM/M30/OMC audio file formats.
 * This class provides utility methods for parsing encrypted audio files used in O2Jam.
 */
public class OJMParser
{
    /** the xor mask used in the M30 format */
    private static final byte[] MASK_NAMI = new byte[]{0x6E, 0x61, 0x6D, 0x69}; // nami
    private static final byte[] MASK_0412 = new byte[]{0x30, 0x34, 0x31, 0x32}; // 0412


    /** the M30 signature, "M30\0" in little endian */
    private static final int M30_SIGNATURE = 0x0030334D;

    /** the OMC signature, "OMC\0" in little endian */
    private static final int OMC_SIGNATURE = 0x00434D4F;

    /** the OJM signature, "OJM\0" in little endian */
    private static final int OJM_SIGNATURE = 0x004D4A4F;

    /** Maximum allowed sample size (50MB) to prevent OOM attacks */
    private static final int MAX_ALLOWED_SAMPLE_SIZE = 50 * 1024 * 1024;

    /* this is a dump from debugging notetool */
    private static final byte[] REARRANGE_TABLE = new byte[]{
    0x10, 0x0E, 0x02, 0x09, 0x04, 0x00, 0x07, 0x01,
    0x06, 0x08, 0x0F, 0x0A, 0x05, 0x0C, 0x03, 0x0D,
    0x0B, 0x07, 0x02, 0x0A, 0x0B, 0x03, 0x05, 0x0D,
    0x08, 0x04, 0x00, 0x0C, 0x06, 0x0F, 0x0E, 0x10,
    0x01, 0x09, 0x0C, 0x0D, 0x03, 0x00, 0x06, 0x09,
    0x0A, 0x01, 0x07, 0x08, 0x10, 0x02, 0x0B, 0x0E,
    0x04, 0x0F, 0x05, 0x08, 0x03, 0x04, 0x0D, 0x06,
    0x05, 0x0B, 0x10, 0x02, 0x0C, 0x07, 0x09, 0x0A,
    0x0F, 0x0E, 0x00, 0x01, 0x0F, 0x02, 0x0C, 0x0D,
    0x00, 0x04, 0x01, 0x05, 0x07, 0x03, 0x09, 0x10,
    0x06, 0x0B, 0x0A, 0x08, 0x0E, 0x00, 0x04, 0x0B,
    0x10, 0x0F, 0x0D, 0x0C, 0x06, 0x05, 0x07, 0x01,
    0x02, 0x03, 0x08, 0x09, 0x0A, 0x0E, 0x03, 0x10,
    0x08, 0x07, 0x06, 0x09, 0x0E, 0x0D, 0x00, 0x0A,
    0x0B, 0x04, 0x05, 0x0C, 0x02, 0x01, 0x0F, 0x04,
    0x0E, 0x10, 0x0F, 0x05, 0x08, 0x07, 0x0B, 0x00,
    0x01, 0x06, 0x02, 0x0C, 0x09, 0x03, 0x0A, 0x0D,
    0x06, 0x0D, 0x0E, 0x07, 0x10, 0x0A, 0x0B, 0x00,
    0x01, 0x0C, 0x0F, 0x02, 0x03, 0x08, 0x09, 0x04,
    0x05, 0x0A, 0x0C, 0x00, 0x08, 0x09, 0x0D, 0x03,
    0x04, 0x05, 0x10, 0x0E, 0x0F, 0x01, 0x02, 0x0B,
    0x06, 0x07, 0x05, 0x06, 0x0C, 0x04, 0x0D, 0x0F,
    0x07, 0x0E, 0x08, 0x01, 0x09, 0x02, 0x10, 0x0A,
    0x0B, 0x00, 0x03, 0x0B, 0x0F, 0x04, 0x0E, 0x03,
    0x01, 0x00, 0x02, 0x0D, 0x0C, 0x06, 0x07, 0x05,
    0x10, 0x09, 0x08, 0x0A, 0x03, 0x02, 0x01, 0x00,
    0x04, 0x0C, 0x0D, 0x0B, 0x10, 0x05, 0x06, 0x0F,
    0x0E, 0x07, 0x09, 0x0A, 0x08, 0x09, 0x0A, 0x00,
    0x07, 0x08, 0x06, 0x10, 0x03, 0x04, 0x01, 0x02,
    0x05, 0x0B, 0x0E, 0x0F, 0x0D, 0x0C, 0x0A, 0x06,
    0x09, 0x0C, 0x0B, 0x10, 0x07, 0x08, 0x00, 0x0F,
    0x03, 0x01, 0x02, 0x05, 0x0D, 0x0E, 0x04, 0x0D,
    0x00, 0x01, 0x0E, 0x02, 0x03, 0x08, 0x0B, 0x07,
    0x0C, 0x09, 0x05, 0x0A, 0x0F, 0x04, 0x06, 0x10,
    0x01, 0x0E, 0x02, 0x03, 0x0D, 0x0B, 0x07, 0x00,
    0x08, 0x0C, 0x09, 0x06, 0x0F, 0x10, 0x05, 0x0A,
    0x04, 0x00};

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private OJMParser() {
        throw new UnsupportedOperationException("OJMParser is a utility class and cannot be instantiated");
    }

    public static Map<Integer, SampleData> parseFile(File file)
    {
        Map<Integer, SampleData> ret;
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, 4);
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int signature = buffer.getInt();

            switch(signature)
            {
                case M30_SIGNATURE:
                    ret = parseM30(f, file);
                    break;

                case OMC_SIGNATURE:
                    ret = parseOmc(f, true);
                    break;
                case OJM_SIGNATURE:
                    ret = parseOmc(f, false);
                    break;

                default:
                    Logger.global.warning("Unknown OJM signature !!");
                    ret = new HashMap<>();
            }
        } catch(IOException e) {
            Logger.global.log(Level.WARNING, "IO exception on file {0} : {1}", new Object[]{file.getName(), e.getMessage()});
            ret = new HashMap<>();
        }
        return ret;
    }

    @SuppressWarnings("java:S135") // Legitimate guard clauses for binary stream parsing - break on EOF/invalid data
    private static Map<Integer, SampleData> parseM30(RandomAccessFile f, File file) throws IOException
    {
        ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 4, 28);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // header - read and discard unused fields
        buffer.getInt(); // file_format_version (unused)
        int encryptionFlag = buffer.getInt();
        int sampleCount = buffer.getInt();
        buffer.position(buffer.position() + 12); // skip sample_offset, payload_size, padding (12 bytes)

        buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 28, f.getChannel().size()-28);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        Map<Integer, SampleData> samples = new HashMap<>();

        for(int i = 0; i < sampleCount; i++)
        {
            // reached the end of the file before the samples_count
            if(buffer.remaining() < 52){
                Logger.global.log(Level.INFO, "Wrong number of samples on OJM header : {0}", file.getName());
                break;
            }
            byte[] byteName = new byte[32];
            buffer.get(byteName);
            String sampleName = ByteHelper.toString(byteName);
            if(sampleName.lastIndexOf(".") < 0) sampleName += ".ogg";

            int sampleSize = buffer.getInt();
            // Validate sample_size to prevent OOM attacks
            if (sampleSize < 0 || sampleSize > MAX_ALLOWED_SAMPLE_SIZE || sampleSize > buffer.remaining()) {
                Logger.global.log(Level.WARNING, "Invalid sample size ({0}) in M30 file, skipping", sampleSize);
                break;
            }

            short codecCode = buffer.getShort();
            buffer.getShort(); // codec_code2 (unused)
            buffer.getInt();   // music_flag (unused)
            short ref = buffer.getShort();
            buffer.getShort(); // unk_zero (unused)
            buffer.getInt();   // pcm_samples (unused)

            byte[] sampleData = new byte[sampleSize];
            buffer.get(sampleData);

            switch(encryptionFlag)
            {
                case 0:  break; //Let it pass
                case 16: xorWithMask(sampleData, MASK_NAMI); break;
                case 32: xorWithMask(sampleData, MASK_0412); break;
                default: Logger.global.log(Level.WARNING, "Unknown encryption flag({0}) !", encryptionFlag);
            }

            SampleData audioData = new SampleData(new ByteArrayInputStream(sampleData), SampleData.Type.OGG, sampleName);
            int value = ref;
            if(codecCode == 0){
                value = 1000 + ref;
            }
            else if(codecCode != 5){
               Logger.global.log(Level.WARNING, "Unknown codec code [{0}] on OJM : {1}", new Object[]{codecCode, file.getName()});
            }
            samples.put(value, audioData);
        }
        return samples;
    }

    private static void xorWithMask(byte[] array, byte[] mask)
    {
        for(int i = 0; i + 3 < array.length; i += 4)
        {
            array[i + 0] ^= mask[0];
            array[i + 1] ^= mask[1];
            array[i + 2] ^= mask[2];
            array[i + 3] ^= mask[3];
        }
    }

    @SuppressWarnings("java:S135") // Legitimate guard clauses for binary stream parsing - continue on empty/invalid chunks
    private static Map<Integer, SampleData> parseOmc(RandomAccessFile f, boolean decrypt) throws IOException
    {
       Map<Integer, SampleData> samples =  new HashMap<>();

       ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 4, 16);
       buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

       buffer.getShort(); // unk1 (unused)
       buffer.getShort(); // unk2 (unused)
       buffer.getInt();   // wav_start (unused)
       int oggStart = buffer.getInt();
       int filesize = buffer.getInt();

       int fileOffset = 20;
       int sampleId = 0; // wav samples use id 0~999

       // Initialize decryption state for this parsing session
       int accKeybyte = 0xFF;
       int accCounter = 0;

       while(fileOffset < oggStart) // WAV data
       {
           buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, fileOffset, 56);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           fileOffset += 56;

           byte[] byteName = new byte[32];
           buffer.get(byteName);
           String sampleName = ByteHelper.toString(byteName);
           if(sampleName.lastIndexOf(".") < 0) sampleName += ".wav";

           short audioFormat = buffer.getShort();
           short numChannels = buffer.getShort();
           int sampleRate = buffer.getInt();
           int bitRate = buffer.getInt();
           short blockAlign = buffer.getShort();
           short bitsPerSample = buffer.getShort();
           int data = buffer.getInt();
           int chunkSize = buffer.getInt();

           if(chunkSize == 0){
               sampleId++;
               continue;
           }

           SampleData.WAVHeader header =
                   new SampleData.WAVHeader(audioFormat, numChannels, sampleRate, bitRate, blockAlign, bitsPerSample, data, chunkSize);

           buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, fileOffset, chunkSize);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           fileOffset += chunkSize;

           byte[] buf = new byte[buffer.remaining()];
           buffer.get(buf);

           if(decrypt)
           {
               buf = rearrange(buf);
               int[] decryptState = new int[]{accKeybyte, accCounter};
               buf = xorWithState(buf, decryptState);
               // Update state after XOR operation
               accKeybyte = decryptState[0];
               accCounter = decryptState[1];
           }

           buffer = ByteBuffer.allocateDirect(buf.length);
           buffer.put(buf);
           buffer.flip();

           SampleData audioData = new SampleData(new ByteBufferInputStream(buffer), header, sampleName);
           samples.put(sampleId, audioData);
           sampleId++;
       }
       sampleId = 1000; // ogg samples use id 1000~?
       while(fileOffset < filesize) // OGG data
       {
           buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, fileOffset, 36);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           fileOffset += 36;

           byte[] byteName = new byte[32];
           buffer.get(byteName);
           String sampleName = ByteHelper.toString(byteName);
           if(sampleName.lastIndexOf(".") < 0) sampleName += ".ogg";

           int sampleSize = buffer.getInt();

           if(sampleSize == 0){
               sampleId++;
               continue;
           }
           // Validate sample_size to prevent OOM attacks
           if (sampleSize < 0 || sampleSize > MAX_ALLOWED_SAMPLE_SIZE) {
               Logger.global.log(Level.WARNING, "Invalid sample size ({0}) in OGG section, skipping", sampleSize);
               sampleId++;
               continue;
           }

           buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, fileOffset, sampleSize);
           buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
           fileOffset += sampleSize;

           SampleData audioData = new SampleData(new ByteBufferInputStream(buffer), SampleData.Type.OGG, sampleName);
           samples.put(sampleId, audioData);
           sampleId++;
       }

       return samples;
    }

    /** fuck the person who invented this, FUCK YOU!... but with love =$ */
    private static byte[] rearrange(byte[] bufEncoded)
    {
        int length = bufEncoded.length;
        int key = ((length % 17) << 4) + (length % 17);

        int blockSize = length / 17;

        // Let's fill the buffer
        byte[] bufPlain = new byte[length];
        System.arraycopy(bufEncoded, 0, bufPlain, 0, length);

        for(int block = 0; block < 17; block++) // loopy loop
        {
            int blockStartEncoded = blockSize * block;
            int blockStartPlain = blockSize * REARRANGE_TABLE[key];
            System.arraycopy(bufEncoded, blockStartEncoded, bufPlain, blockStartPlain, blockSize);

            key++;
        }
        return bufPlain;
    }

    /** some weird encryption - uses instance state passed as array for thread safety */
    /** State array: [0] = acc_keybyte, [1] = acc_counter - passed and returned by reference */
    private static byte[] xorWithState(byte[] buf, int[] state)
    {
        int accKeybyte = state[0];
        int accCounter = state[1];
        int temp;
        byte thisByte;
        for(int i = 0; i < buf.length; i++)
        {
            temp = thisByte = buf[i];

            if(((accKeybyte << accCounter) & 0x80) != 0){
                thisByte = (byte) ~thisByte;
            }

            buf[i] = thisByte;
            accCounter++;
            if(accCounter > 7){
                accCounter = 0;
                accKeybyte = temp;
            }
        }
        // Return updated state via array reference
        state[0] = accKeybyte;
        state[1] = accCounter;
        return buf;
    }
}
