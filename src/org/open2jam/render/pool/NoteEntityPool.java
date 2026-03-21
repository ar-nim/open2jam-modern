package org.open2jam.render.pool;

import org.open2jam.parsers.Event;
import org.open2jam.render.entities.NoteEntity;
import org.open2jam.render.entities.LongNoteEntity;
import org.open2jam.render.entities.SampleEntity;

/**
 * Object pool for NoteEntity and LongNoteEntity instances.
 * Eliminates per-note allocations during gameplay by reusing pre-allocated entities.
 *
 * Usage:
 *   NoteEntityPool pool = new NoteEntityPool(maxNotes);
 *   NoteEntity note = pool.acquireNote(channel, time);
 *   // ... use note ...
 *   pool.release(note);  // When note is dead
 */
public class NoteEntityPool
{
    // Separate pool per channel (7 channels for 7-key mode)
    // Each channel has its own x position, so we can't mix channels
    private final NoteEntity[][] notePools;      // [channel][pool index]
    private final boolean[][] noteInUse;
    private final int[] noteCounts;

    private final LongNoteEntity[][] longNotePools;
    private final boolean[][] longNoteInUse;
    private final int[] longNoteCounts;

    /**
     * Create a pool with the specified capacity.
     * @param maxNotes Maximum number of notes (per channel)
     */
    public NoteEntityPool(int maxNotes)
    {
        // 7 channels for 7-key mode (NOTE_1 through NOTE_7)
        int numChannels = 7;
        
        notePools = new NoteEntity[numChannels][];
        noteInUse = new boolean[numChannels][];
        noteCounts = new int[numChannels];

        longNotePools = new LongNoteEntity[numChannels][];
        longNoteInUse = new boolean[numChannels][];
        longNoteCounts = new int[numChannels];

        // Initialize each channel's pool
        for (int i = 0; i < numChannels; i++) {
            notePools[i] = new NoteEntity[maxNotes];
            noteInUse[i] = new boolean[maxNotes];
            noteCounts[i] = 0;

            longNotePools[i] = new LongNoteEntity[maxNotes];
            longNoteInUse[i] = new boolean[maxNotes];
            longNoteCounts[i] = 0;
        }
    }

    /**
     * Initialize pool with prototype entities.
     * Call this once during skin loading.
     * @param notePrototypes Array of 7 note prototypes (one per channel)
     * @param longNotePrototypes Array of 7 long note prototypes (one per channel)
     */
    public void initializePrototypes(
        NoteEntity[] notePrototypes,
        LongNoteEntity[] longNotePrototypes)
    {
        // Create regular note instances from prototypes (per channel)
        for (int ch = 0; ch < 7 && ch < notePrototypes.length; ch++) {
            if (notePrototypes[ch] == null) continue;
            
            for (int i = 0; i < notePools[ch].length; i++) {
                notePools[ch][i] = (NoteEntity) notePrototypes[ch].copy();
                noteCounts[ch]++;
            }
        }

        // Create long note instances from prototypes (per channel)
        for (int ch = 0; ch < 7 && ch < longNotePrototypes.length; ch++) {
            if (longNotePrototypes[ch] == null) continue;
            
            for (int i = 0; i < longNotePools[ch].length; i++) {
                longNotePools[ch][i] = (LongNoteEntity) longNotePrototypes[ch].copy();
                longNoteCounts[ch]++;
            }
        }
    }

    /**
     * Acquire a regular note entity from the pool.
     * @param channel The note channel
     * @param time The time when this note should be hit
     * @return A pooled NoteEntity, or null if pool exhausted
     */
    public NoteEntity acquireNote(Event.Channel channel, double time)
    {
        int chIndex = channelToIndex(channel);
        if (chIndex < 0 || chIndex >= 7) return null;

        for (int i = 0; i < noteCounts[chIndex]; i++) {
            if (!noteInUse[chIndex][i]) {
                noteInUse[chIndex][i] = true;
                NoteEntity note = notePools[chIndex][i];
                // Reset and initialize
                note.reset();
                note.initialize(channel, time);
                return note;
            }
        }
        // Pool exhausted - return null (should not happen with proper sizing)
        return null;
    }

    /**
     * Acquire a long note entity from the pool.
     * @param channel The note channel
     * @param time The time when this note should be hit
     * @return A pooled LongNoteEntity, or null if pool exhausted
     */
    public LongNoteEntity acquireLongNote(Event.Channel channel, double time)
    {
        int chIndex = channelToIndex(channel);
        if (chIndex < 0 || chIndex >= 7) return null;

        for (int i = 0; i < longNoteCounts[chIndex]; i++) {
            if (!longNoteInUse[chIndex][i]) {
                longNoteInUse[chIndex][i] = true;
                LongNoteEntity note = longNotePools[chIndex][i];
                // Reset and initialize
                note.reset();
                note.initialize(channel, time);
                return note;
            }
        }
        // Pool exhausted - return null (should not happen with proper sizing)
        return null;
    }

    /**
     * Release a note entity back to the pool.
     * Call this when a note is dead (off-screen or judged).
     */
    public void release(NoteEntity note)
    {
        int chIndex = channelToIndex(note.getChannel());
        if (chIndex < 0 || chIndex >= 7) return;

        for (int i = 0; i < noteCounts[chIndex]; i++) {
            if (notePools[chIndex][i] == note) {
                noteInUse[chIndex][i] = false;
                return;
            }
        }
    }

    /**
     * Release a long note entity back to the pool.
     * Call this when a note is dead (off-screen or judged).
     */
    public void release(LongNoteEntity note)
    {
        int chIndex = channelToIndex(note.getChannel());
        if (chIndex < 0 || chIndex >= 7) return;

        for (int i = 0; i < longNoteCounts[chIndex]; i++) {
            if (longNotePools[chIndex][i] == note) {
                longNoteInUse[chIndex][i] = false;
                return;
            }
        }
    }

    /**
     * Convert Event.Channel to array index (0-6).
     */
    private int channelToIndex(Event.Channel channel)
    {
        switch (channel) {
            case NOTE_1: return 0;
            case NOTE_2: return 1;
            case NOTE_3: return 2;
            case NOTE_4: return 3;
            case NOTE_5: return 4;
            case NOTE_6: return 5;
            case NOTE_7: return 6;
            default: return -1;
        }
    }

    /**
     * Reset the entire pool (mark all as unused).
     * Call this at the start of each song.
     */
    public void reset()
    {
        for (int ch = 0; ch < 7; ch++) {
            for (int i = 0; i < noteInUse[ch].length; i++) {
                noteInUse[ch][i] = false;
            }
            for (int i = 0; i < longNoteInUse[ch].length; i++) {
                longNoteInUse[ch][i] = false;
            }
        }
    }

    /**
     * Get the number of notes currently in use.
     */
    public int getActiveNoteCount()
    {
        int count = 0;
        for (int ch = 0; ch < 7; ch++) {
            for (boolean inUse : noteInUse[ch]) {
                if (inUse) count++;
            }
        }
        return count;
    }

    /**
     * Get the number of long notes currently in use.
     */
    public int getActiveLongNoteCount()
    {
        int count = 0;
        for (int ch = 0; ch < 7; ch++) {
            for (boolean inUse : longNoteInUse[ch]) {
                if (inUse) count++;
            }
        }
        return count;
    }

    /**
     * Get total pool capacity for regular notes.
     */
    public int getNoteCapacity()
    {
        int total = 0;
        for (int count : noteCounts) total += count;
        return total;
    }

    /**
     * Get total pool capacity for long notes.
     */
    public int getLongNoteCapacity()
    {
        int total = 0;
        for (int count : longNoteCounts) total += count;
        return total;
    }
}
