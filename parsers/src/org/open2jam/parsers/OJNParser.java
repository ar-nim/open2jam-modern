package org.open2jam.parsers;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.logging.Level;

import org.open2jam.parsers.utils.ByteHelper;
import org.open2jam.parsers.utils.Logger;

/**
 * Parser for OJN (O2Jam Note) chart files.
 * This class provides static methods only and cannot be instantiated.
 */
class OJNParser {
    private static final String[] GENRE_MAP = { "Ballad", "Rock", "Dance", "Techno", "Hip-hop",
            "Soul/R&B", "Jazz", "Funk", "Classical", "Traditional", "Etc" };

    /** the signature that appears at offset 4, "ojn\0" in little endian */
    private static final int OJN_SIGNATURE = 0x006E6A6F;

    /** Private constructor to prevent instantiation (utility class). */
    private OJNParser() {
        // Utility class - prevent instantiation
    }

    public static boolean canRead(File file) {
        return file.getName().toLowerCase().endsWith(".ojn");
    }

    public static ChartList parseFile(File file) {
        ByteBuffer buffer;
        try (RandomAccessFileChannelWrapper fileWrapper = new RandomAccessFileChannelWrapper(file)) {
            buffer = fileWrapper.map(0, 300);
        } catch (IOException e) {
            Logger.global.log(Level.WARNING, "IO exception on reading OJN file {0}", file.getName());
            return new ChartList();
        }

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        OJNChart easy = new OJNChart();
        OJNChart normal = new OJNChart();
        OJNChart hard = new OJNChart();

        try {
            buffer.getInt(); // songid - unused
            int signature = buffer.getInt();
            if (signature != OJN_SIGNATURE) {
                Logger.global.log(Level.WARNING, "File [{0}] isn''t a OJN file !", file);
                return new ChartList();
            }

            buffer.getFloat(); // encode_version - unused

            int genre = buffer.getInt();
            String strGenre = GENRE_MAP[(genre < 0 || genre > 10) ? 10 : genre];
            easy.genre = strGenre;
            normal.genre = strGenre;
            hard.genre = strGenre;

            float bpm = buffer.getFloat();
            easy.bpm = bpm;
            normal.bpm = bpm;
            hard.bpm = bpm;

            easy.level = buffer.getShort();
            normal.level = buffer.getShort();
            hard.level = buffer.getShort();
            buffer.getShort(); // 0, always

            int[] eventCount = new int[3];
            eventCount[0] = buffer.getInt();
            eventCount[1] = buffer.getInt();
            eventCount[2] = buffer.getInt();

            easy.notes = buffer.getInt();
            normal.notes = buffer.getInt();
            hard.notes = buffer.getInt();

            int[] measureCount = new int[3];
            measureCount[0] = buffer.getInt();
            measureCount[1] = buffer.getInt();
            measureCount[2] = buffer.getInt();
            int[] packageCount = new int[3];
            packageCount[0] = buffer.getInt();
            packageCount[1] = buffer.getInt();
            packageCount[2] = buffer.getInt();
            buffer.getShort(); // old_encode_version
            buffer.getShort(); // old_songid
            byte[] oldGenre = new byte[20];
            buffer.get(oldGenre);
            buffer.getInt(); // bmp_size
            buffer.getInt(); // file_version

            byte[] title = new byte[64];
            buffer.get(title);
            String strTitle = ByteHelper.toString(title);
            easy.title = strTitle;
            normal.title = strTitle;
            hard.title = strTitle;

            byte[] artist = new byte[32];
            buffer.get(artist);
            String strArtist = ByteHelper.toString(artist);
            easy.artist = strArtist;
            normal.artist = strArtist;
            hard.artist = strArtist;

            byte[] noter = new byte[32];
            buffer.get(noter);
            String strNoter = ByteHelper.toString(noter);
            easy.noter = strNoter;
            normal.noter = strNoter;
            hard.noter = strNoter;

            byte[] ojmFile = new byte[32];
            buffer.get(ojmFile);
            // Sanitize filename to prevent path traversal attacks
            String ojmFilename = ByteHelper.toString(ojmFile);
            ojmFilename = new File(ojmFilename).getName(); // Strip path components
            File sampleFile = new File(file.getParent(), ojmFilename);
            easy.sampleFile = sampleFile;
            normal.sampleFile = sampleFile;
            hard.sampleFile = sampleFile;

            int coverSize = buffer.getInt();
            easy.coverSize = coverSize;
            normal.coverSize = coverSize;
            hard.coverSize = coverSize;

            easy.duration = buffer.getInt();
            normal.duration = buffer.getInt();
            hard.duration = buffer.getInt();

            easy.noteOffset = buffer.getInt();
            normal.noteOffset = buffer.getInt();
            hard.noteOffset = buffer.getInt();
            int coverOffset = buffer.getInt();

            easy.noteOffsetEnd = normal.noteOffset;
            normal.noteOffsetEnd = hard.noteOffset;
            hard.noteOffsetEnd = coverOffset;

            easy.coverOffset = coverOffset;
            normal.coverOffset = coverOffset;
            hard.coverOffset = coverOffset;

            easy.source = file;
            normal.source = file;
            hard.source = file;

            ChartList list = new ChartList();
            list.add(easy);
            list.add(normal);
            list.add(hard);

            list.sourceFile = file;
            buffer.clear();

            return list;
        } catch (java.nio.BufferUnderflowException e) {
            Logger.global.log(Level.WARNING, "Malformed OJN file (truncated or corrupted): {0}", file.getName());
            return new ChartList();
        }
    }

    public static EventList parseChart(OJNChart chart) {
        EventList eventList = new EventList();
        try (RandomAccessFileChannelWrapper fileWrapper = new RandomAccessFileChannelWrapper(chart.getSource())) {
            int start = chart.noteOffset;
            int end = chart.noteOffsetEnd;

            ByteBuffer buffer = fileWrapper.map(start, (int) ((long) end - start));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            readNoteBlock(eventList, buffer);
        } catch (java.io.FileNotFoundException e) {
            Logger.global.log(Level.WARNING, "File {0} not found !!", chart.getSource().getName());
        } catch (IOException e) {
            Logger.global.log(Level.WARNING, "IO exception on reading OJN file {0}", chart.getSource().getName());
        }
        return eventList;
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

        @Override
        public void close() throws IOException {
            channel.close();
            file.close();
        }
    }

    /**
     * Reads note events from a binary buffer.
     * Delegates to specialized parsing methods based on channel type.
     */
    private static void readNoteBlock(EventList eventList, ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            int measure = buffer.getInt();
            short channelNumber = buffer.getShort();
            short eventsCount = buffer.getShort();

            Event.Channel channel = mapChannelNumber(channelNumber);

            // Branch outside the loop to reduce cognitive complexity
            if (channel == Event.Channel.BPM_CHANGE || channel == Event.Channel.TIME_SIGNATURE) {
                parseTimingEvents(eventList, buffer, channel, measure, eventsCount);
            } else {
                parseNoteEvents(eventList, buffer, channel, measure, eventsCount);
            }
        }
        Collections.sort(eventList);
    }

    /**
     * Parses timing events (BPM changes and time signatures).
     * These events contain only a single float value.
     */
    private static void parseTimingEvents(EventList eventList, ByteBuffer buffer, Event.Channel channel, int measure, short eventsCount) {
        for (int i = 0; i < eventsCount; i++) {
            double position = (double) i / eventsCount;
            float v = buffer.getFloat();

            if (v != 0) {
                eventList.add(new Event(channel, measure, position, v, Event.Flag.NONE));
            }
        }
    }

    /**
     * Parses note events with volume and pan information.
     * These events contain: value (short), volume/pan (byte), type (byte).
     */
    private static void parseNoteEvents(EventList eventList, ByteBuffer buffer, Event.Channel channel, int measure, short eventsCount) {
        for (int i = 0; i < eventsCount; i++) {
            double position = (double) i / eventsCount;
            short value = buffer.getShort();
            int volumePan = buffer.get();
            int type = buffer.get();

            if (value == 0) {
                continue; // ignore value=0 events
            }

            // MIN 1 ~ 15 MAX, special 0 = MAX
            float volume = ((volumePan >> 4) & 0x0F) / 15f;
            if (volume == 0) {
                volume = 1.0f; // Special case: 0 = MAX (full volume)
            }

            // LEFT 1 ~ 8 CENTER 8 ~ 15 RIGHT, special: 0 = 8
            float pan = (volumePan & 0x0F);
            if (pan == 0) {
                pan = 8;
            }
            pan = (pan - 8) / 7.0f; // Correct formula: -1.0 (left) to +1.0 (right)

            value--; // make zero-based (zero was the "ignore" value)

            NoteTypeResult result = parseNoteType(type, value);
            eventList.add(new Event(channel, measure, position, result.value, result.flag, volume, pan));
        }
    }

    /**
     * Result holder for note type parsing.
     */
    private static final class NoteTypeResult {
        final Event.Flag flag;
        final short value;

        NoteTypeResult(Event.Flag flag, short value) {
            this.flag = flag;
            this.value = value;
        }
    }

    /**
     * Parses note type and adjusts value for long notes.
     * Returns both the Event.Flag and the (potentially modified) value.
     */
    private static NoteTypeResult parseNoteType(int type, short value) {
        // Adjust value for long notes (adds 1000 to distinguish from regular notes)
        if (type % 8 > 3) {
            value += 1000;
        }
        type %= 4;

        Event.Flag flag = switch (type) {
            case 0 -> Event.Flag.NONE;
            case 1 -> Event.Flag.NONE; // Unused (#W Normal displayed in NoteTool)
            case 2 -> Event.Flag.HOLD; // fix for autoplay longnotes
            case 3 -> Event.Flag.RELEASE;
            default -> Event.Flag.NONE; // Should not happen due to type %= 4
        };

        return new NoteTypeResult(flag, value);
    }

    /**
     * Maps channel number to Event.Channel enum.
     */
    private static Event.Channel mapChannelNumber(short channelNumber) {
        switch (channelNumber) {
            case 0:
                return Event.Channel.TIME_SIGNATURE;
            case 1:
                return Event.Channel.BPM_CHANGE;
            case 2:
                return Event.Channel.NOTE_1;
            case 3:
                return Event.Channel.NOTE_2;
            case 4:
                return Event.Channel.NOTE_3;
            case 5:
                return Event.Channel.NOTE_4;
            case 6:
                return Event.Channel.NOTE_5;
            case 7:
                return Event.Channel.NOTE_6;
            case 8:
                return Event.Channel.NOTE_7;
            default:
                return Event.Channel.AUTO_PLAY;
        }
    }
}
