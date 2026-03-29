package org.open2jam.parsers;

/**
 * Data Transfer Object for chart metadata from SQLite cache.
 * Lightweight - contains only cached fields, not full chart data.
 *
 * <p>Memory Safety: This class holds only primitive types and small strings.
 * Large binary data (cover art) is stored as byte[] but should be loaded lazily.</p>
 *
 * <p>Thread Safety: This class is NOT thread-safe. Fields are private with accessors
 * for encapsulation. Synchronize access if modified from multiple threads.</p>
 *
 * @author open2jam-modern team
 */
public class ChartMetadata {
    // ===== IDENTIFICATION =====
    private int id;                         // Database row ID
    private int libraryId;                  // Foreign key to libraries table
    private String relativePath;            // Path relative to library root (forward slashes)
    private String songGroupId;             // MD5 hash of library_id:relative_path
    private String chartListHash;           // Hash of relative_path:last_modified

    // ===== FILE STATS =====
    private long sourceFileSize;            // File size in bytes
    private long sourceFileModified;        // File last modified timestamp

    // ===== CHART INFO =====
    private String chartType;               // BMS, OJN, SM, XNT
    private int chartIndex;                 // 0=Easy, 1=Normal, 2=Hard (for OJN)

    // ===== IDENTITY (Lazy) =====
    private String sha256Hash;              // SHA-256 of note data (NULL until calculated)

    // ===== METADATA =====
    private String title;                   // Chart title
    private String artist;                  // Chart artist
    private String genre;                   // Chart genre
    private String noter;                   // Person who created the chart
    private int level;                      // Difficulty level (1-25)
    private int keys;                       // Number of keys (4-8)
    private int players;                    // Number of players (1-2)
    private double bpm;                     // Beats per minute
    private int notes;                      // Total note count
    private int duration;                   // Song duration in seconds

    // ===== BINARY OFFSETS (OJN-specific) =====
    // Note: cover_offset and cover_size are stored directly in chart_cache table
    private Integer coverOffset;            // Byte offset of embedded cover art
    private Integer coverSize;              // Size of embedded cover in bytes
    private String coverExternalPath;       // Path to external cover file (BMS, SM)
    private Integer noteDataOffset;         // Byte offset of note data
    private Integer noteDataSize;           // Size of note data in bytes

    // ===== CACHE INFO =====
    private long cachedAt;                  // Unix timestamp when cached

    // ===== RUNTIME (not stored in DB, populated at runtime) =====
    private String libraryRootPath;         // Populated when joining with libraries table

    // ===== GETTERS AND SETTERS =====

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getLibraryId() { return libraryId; }
    public void setLibraryId(int libraryId) { this.libraryId = libraryId; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getSongGroupId() { return songGroupId; }
    public void setSongGroupId(String songGroupId) { this.songGroupId = songGroupId; }

    public String getChartListHash() { return chartListHash; }
    public void setChartListHash(String chartListHash) { this.chartListHash = chartListHash; }

    public long getSourceFileSize() { return sourceFileSize; }
    public void setSourceFileSize(long sourceFileSize) { this.sourceFileSize = sourceFileSize; }

    public long getSourceFileModified() { return sourceFileModified; }
    public void setSourceFileModified(long sourceFileModified) { this.sourceFileModified = sourceFileModified; }

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }

    public int getChartIndex() { return chartIndex; }
    public void setChartIndex(int chartIndex) { this.chartIndex = chartIndex; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getNoter() { return noter; }
    public void setNoter(String noter) { this.noter = noter; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getKeys() { return keys; }
    public void setKeys(int keys) { this.keys = keys; }

    public int getPlayers() { return players; }
    public void setPlayers(int players) { this.players = players; }

    public double getBpm() { return bpm; }
    public void setBpm(double bpm) { this.bpm = bpm; }

    public int getNotes() { return notes; }
    public void setNotes(int notes) { this.notes = notes; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public Integer getCoverOffset() { return coverOffset; }
    public void setCoverOffset(Integer coverOffset) { this.coverOffset = coverOffset; }

    public Integer getCoverSize() { return coverSize; }
    public void setCoverSize(Integer coverSize) { this.coverSize = coverSize; }

    public String getCoverExternalPath() { return coverExternalPath; }
    public void setCoverExternalPath(String coverExternalPath) { this.coverExternalPath = coverExternalPath; }

    public Integer getNoteDataOffset() { return noteDataOffset; }
    public void setNoteDataOffset(Integer noteDataOffset) { this.noteDataOffset = noteDataOffset; }

    public Integer getNoteDataSize() { return noteDataSize; }
    public void setNoteDataSize(Integer noteDataSize) { this.noteDataSize = noteDataSize; }

    public long getCachedAt() { return cachedAt; }
    public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }

    public String getLibraryRootPath() { return libraryRootPath; }
    public void setLibraryRootPath(String libraryRootPath) { this.libraryRootPath = libraryRootPath; }

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
        // Handle trailing slash in libraryRootPath
        String root = libraryRootPath.endsWith("/") ? libraryRootPath.substring(0, libraryRootPath.length() - 1) : libraryRootPath;
        return root + "/" + relativePath;
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
