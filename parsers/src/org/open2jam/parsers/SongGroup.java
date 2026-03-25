package org.open2jam.parsers;

import java.util.List;

/**
 * Represents a song with multiple difficulties (Easy/Normal/Hard).
 * Used for UI grouping in song selection.
 * 
 * <p>Thread Safety: This class is immutable except for the difficulties field,
 * which should be populated once and never modified. Treat difficulties as read-only
 * after initial assignment.</p>
 * 
 * @author open2jam-modern team
 */
public class SongGroup {
    /** Unique identifier for this song group (MD5 hash) */
    public final String songGroupId;
    
    /** Song title (from first difficulty) */
    public final String title;
    
    /** Song artist (from first difficulty) */
    public final String artist;
    
    /** Number of difficulties in this group (1-3 for OJN, 1 for BMS/SM/XNT) */
    public final int diffCount;
    
    /** Minimum level across all difficulties */
    public final int minLevel;
    
    /** Maximum level across all difficulties */
    public final int maxLevel;
    
    /** 
     * List of all difficulties in this group.
     * Populated lazily when user expands the song in UI.
     * May be null if not yet loaded.
     */
    public List<ChartMetadata> difficulties;

    /**
     * Construct a SongGroup from aggregated query results.
     * 
     * @param songGroupId Unique group identifier
     * @param title Song title
     * @param artist Song artist
     * @param diffCount Number of difficulties
     * @param minLevel Minimum difficulty level
     * @param maxLevel Maximum difficulty level
     */
    public SongGroup(String songGroupId, String title, String artist,
                     int diffCount, int minLevel, int maxLevel) {
        this.songGroupId = songGroupId;
        this.title = title;
        this.artist = artist;
        this.diffCount = diffCount;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.difficulties = null;  // Lazily loaded
    }

    /**
     * Check if this song has multiple difficulties.
     * 
     * @return true if diffCount > 1
     */
    public boolean hasMultipleDifficulties() {
        return diffCount > 1;
    }

    /**
     * Get a formatted difficulty count string for UI display.
     * 
     * @return "[1 diff]" or "[2 diffs]" or "[3 diffs]"
     */
    public String getDiffCountString() {
        return diffCount == 1 ? "[1 diff]" : "[" + diffCount + " diffs]";
    }

    /**
     * Get a formatted level range string for UI display.
     * 
     * @return "[5]" for single diff, or "[5-12]" for multiple diffs
     */
    public String getLevelRangeString() {
        if (diffCount == 1) {
            return "[" + minLevel + "]";
        } else {
            return "[" + minLevel + "-" + maxLevel + "]";
        }
    }

    /**
     * Get a formatted display label for UI.
     * 
     * @return "Title - Artist [3 diffs: 5-12]"
     */
    public String getDisplayLabel() {
        return String.format("%s - %s %s %s",
            title, artist, getDiffCountString(), getLevelRangeString());
    }

    @Override
    public String toString() {
        return "SongGroup{" +
               "songGroupId='" + songGroupId + '\'' +
               ", title='" + title + '\'' +
               ", artist='" + artist + '\'' +
               ", diffCount=" + diffCount +
               ", levelRange=" + minLevel + "-" + maxLevel +
               '}';
    }
}
