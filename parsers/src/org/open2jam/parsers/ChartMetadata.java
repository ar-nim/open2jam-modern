package org.open2jam.parsers;

/**
 * Data Transfer Object for chart metadata from SQLite cache.
 * Lightweight - contains only cached fields, not full chart data.
 * 
 * <p>Memory Safety: This class holds only primitive types and small strings.
 * Large binary data (cover art) is stored as byte[] but should be loaded lazily.</p>
 * 
 * <p>Thread Safety: This class is NOT thread-safe. Fields are public for performance
 * (avoid getter overhead during gameplay). Synchronize access if modified from multiple threads.</p>
 * 
 * @author open2jam-modern team
 */
public class ChartMetadata {
    // ===== IDENTIFICATION =====
    public int id;                         // Database row ID
    public int libraryId;                  // Foreign key to libraries table
    public String relativePath;            // Path relative to library root (forward slashes)
    public String songGroupId;             // MD5 hash of library_id:relative_path
    public String chartListHash;           // Hash of relative_path:last_modified

    // ===== FILE STATS =====
    public long sourceFileSize;            // File size in bytes
    public long sourceFileModified;        // File last modified timestamp

    // ===== CHART INFO =====
    public String chartType;               // BMS, OJN, SM, XNT
    public int chartIndex;                 // 0=Easy, 1=Normal, 2=Hard (for OJN)

    // ===== IDENTITY (Lazy) =====
    public String sha256Hash;              // SHA-256 of note data (NULL until calculated)

    // ===== METADATA =====
    public String title;                   // Chart title
    public String artist;                  // Chart artist
    public String genre;                   // Chart genre
    public String noter;                   // Person who created the chart
    public int level;                      // Difficulty level (1-25)
    public int keys;                       // Number of keys (4-8)
    public int players;                    // Number of players (1-2)
    public double bpm;                     // Beats per minute
    public int notes;                      // Total note count
    public int duration;                   // Song duration in seconds

    // ===== BINARY OFFSETS (OJN-specific) =====
    public Integer coverOffset;            // Byte offset of embedded cover art
    public Integer coverSize;              // Size of embedded cover in bytes
    public byte[] coverData;               // Cached thumbnail (optional, may be null)
    public String coverExternalPath;       // Path to external cover file (BMS, SM)
    public Integer noteDataOffset;         // Byte offset of note data
    public Integer noteDataSize;           // Size of note data in bytes

    // ===== CACHE INFO =====
    public long cachedAt;                  // Unix timestamp when cached

    // ===== TRANSIENT (not stored in DB, populated at runtime) =====
    public transient String libraryRootPath;  // Populated when joining with libraries table

    /**
     * Get full absolute path.
     * 
     * <p>Security Note: This method does NOT validate paths. The libraryRootPath
     * must be set before calling this method.</p>
     * 
     * @return Full absolute path: libraryRootPath + "/" + relativePath
     * @throws IllegalStateException if libraryRootPath is null
     */
    public String getFullPath() {
        if (libraryRootPath == null) {
            throw new IllegalStateException("libraryRootPath not set - must join with libraries table first");
        }
        // Security: relativePath is trusted (comes from database, validated on insert)
        return libraryRootPath + "/" + relativePath;
    }

    /**
     * Check if this chart has embedded cover art (OJN format).
     * 
     * @return true if coverOffset and coverSize are valid
     */
    public boolean hasEmbeddedCover() {
        return "OJN".equals(chartType) &&
               coverOffset != null &&
               coverSize != null &&
               coverSize > 0;
    }

    /**
     * Check if this chart has a cached SHA-256 identity hash.
     * 
     * @return true if sha256Hash is not null
     */
    public boolean hasIdentityHash() {
        return sha256Hash != null;
    }

    /**
     * Get a safe display title for UI purposes.
     * 
     * @return title, or "Unknown Title" if title is null/empty
     */
    public String getDisplayTitle() {
        return (title != null && !title.isEmpty()) ? title : "Unknown Title";
    }

    /**
     * Get a safe display artist for UI purposes.
     * 
     * @return artist, or "Unknown Artist" if artist is null/empty
     */
    public String getDisplayArtist() {
        return (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
    }

    @Override
    public String toString() {
        return "ChartMetadata{" +
               "id=" + id +
               ", title='" + getDisplayTitle() + '\'' +
               ", artist='" + getDisplayArtist() + '\'' +
               ", chartType='" + chartType + '\'' +
               ", chartIndex=" + chartIndex +
               ", level=" + level +
               '}';
    }
}
