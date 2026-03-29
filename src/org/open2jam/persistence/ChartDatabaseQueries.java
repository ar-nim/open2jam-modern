package org.open2jam.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.open2jam.parsers.ChartMetadata;

/**
 * SQL query definitions for ChartDatabase.
 * 
 * This class centralizes all SQL queries to improve maintainability and readability.
 * 
 * @author open2jam-modern team
 */
public final class ChartDatabaseQueries {

    /**
     * Complete database schema definition.
     */
    public static final String CREATE_TABLES_SQL = """
        -- Enable WAL mode for better crash recovery and concurrent reads
        PRAGMA journal_mode = WAL;

        -- Libraries table: root directories containing chart files
        CREATE TABLE IF NOT EXISTS libraries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            root_path TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            added_at INTEGER NOT NULL,
            last_scan INTEGER,
            is_active INTEGER DEFAULT 1,
            display_order INTEGER NOT NULL
        );

        -- Chart metadata cache
        CREATE TABLE IF NOT EXISTS chart_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            library_id INTEGER NOT NULL,
            relative_path TEXT NOT NULL,
            song_group_id TEXT NOT NULL,
            chart_list_hash TEXT NOT NULL,
            source_file_size INTEGER NOT NULL,
            source_file_modified INTEGER NOT NULL,
            chart_type TEXT NOT NULL,
            chart_index INTEGER NOT NULL,
            sha256_hash TEXT,
            title TEXT NOT NULL,
            artist TEXT NOT NULL,
            genre TEXT,
            noter TEXT,
            level INTEGER,
            keys INTEGER,
            players INTEGER,
            bpm REAL,
            notes INTEGER,
            duration INTEGER,
            note_data_offset INTEGER,
            note_data_size INTEGER,
            cover_offset INTEGER,
            cover_size INTEGER,
            cover_external_path TEXT,
            cached_at INTEGER NOT NULL,
            FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE CASCADE
        );

        -- Performance indexes
        CREATE INDEX IF NOT EXISTS idx_chart_cache_library ON chart_cache(library_id);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_song_group ON chart_cache(song_group_id);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_relative_path ON chart_cache(relative_path);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_title ON chart_cache(title);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_artist ON chart_cache(artist);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_level ON chart_cache(level);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_type ON chart_cache(chart_type);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_identity ON chart_cache(sha256_hash);
        CREATE INDEX IF NOT EXISTS idx_chart_cache_library_path ON chart_cache(library_id, relative_path);

        -- Schema version tracking
        CREATE TABLE IF NOT EXISTS schema_version (
            version INTEGER PRIMARY KEY,
            applied_at INTEGER NOT NULL
        );

        INSERT OR IGNORE INTO schema_version (version, applied_at)
        VALUES (1, strftime('%s', 'now') * 1000);
        """;

    // Library operations
    public static final String INSERT_LIBRARY_SQL =
        "INSERT OR IGNORE INTO libraries (root_path, name, added_at, is_active) VALUES (?, ?, ?, 1)";

    public static final String GET_LIBRARY_BY_ID_SQL =
        "SELECT id, root_path, name, added_at, last_scan, is_active FROM libraries WHERE id = ?";

    public static final String GET_ALL_LIBRARIES_SQL =
        "SELECT id, root_path, name, added_at, last_scan, is_active, display_order " +
        "FROM libraries ORDER BY display_order ASC";

    public static final String UPDATE_LIBRARY_ROOT_SQL =
        "UPDATE libraries SET root_path = ? WHERE id = ?";

    public static final String UPDATE_LIBRARY_SCAN_TIME_SQL =
        "UPDATE libraries SET last_scan = ? WHERE id = ?";

    // Thumbnail operations
    public static final String INSERT_THUMBNAIL_SQL = """
        INSERT OR REPLACE INTO thumbnail (
            song_group_id, cover_offset, cover_size, thumbnail_data, thumbnail_size, cached_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        """;

    // Chart cache operations
    public static final String INSERT_CHART_SQL = """
        INSERT OR REPLACE INTO chart_cache (
            library_id, relative_path, song_group_id, chart_list_hash,
            source_file_size, source_file_modified, chart_type, chart_index,
            title, artist, genre, noter, level, keys, players, bpm, notes, duration,
            note_data_offset, note_data_size, cover_offset, cover_size, cover_external_path, cached_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    public static final String GET_METADATA_BY_PATH_SQL = """
        SELECT c.*, l.root_path as library_root_path
        FROM chart_cache c
        JOIN libraries l ON c.library_id = l.id
        WHERE c.library_id = ? AND c.relative_path = ?
        """;

    public static final String GET_CACHED_CHARTS_SQL = """
        SELECT c.*, l.root_path as library_root_path
        FROM chart_cache c
        JOIN libraries l ON c.library_id = l.id
        WHERE c.library_id = ? AND l.is_active = 1
        ORDER BY c.song_group_id, c.chart_index
        """;

    public static final String GET_SONG_GROUPS_SQL = """
        SELECT song_group_id, title, artist,
               COUNT(*) as diff_count, MIN(level) as min_level, MAX(level) as max_level
        FROM chart_cache
        WHERE library_id = ?
        GROUP BY song_group_id
        ORDER BY title
        """;

    public static final String GET_DIFFICULTIES_FOR_SONG_SQL = """
        SELECT c.id, c.library_id, c.relative_path, c.song_group_id, c.chart_list_hash,
               c.source_file_size, c.source_file_modified, c.chart_type, c.chart_index,
               c.sha256_hash, c.title, c.artist, c.genre, c.noter, c.level, c.keys, c.players, c.bpm,
               c.notes, c.duration, c.note_data_offset, c.note_data_size,
               c.cover_offset, c.cover_size,
               c.cached_at, l.root_path as library_root_path
        FROM chart_cache c
        JOIN libraries l ON c.library_id = l.id
        WHERE c.song_group_id = ?
        ORDER BY c.chart_index
        """;

    public static final String DELETE_CHART_SQL =
        "DELETE FROM chart_cache WHERE id = ?";

    public static final String DELETE_CHARTS_FOR_LIBRARY_SQL =
        "DELETE FROM chart_cache WHERE library_id = ?";

    public static final String UPDATE_HASH_SQL =
        "UPDATE chart_cache SET sha256_hash = ? WHERE id = ?";

    public static final String GET_LIBRARY_BY_PATH_SQL =
        "SELECT id, root_path, name, added_at, last_scan, is_active FROM libraries WHERE root_path = ?";

    public static final String GET_NEXT_DISPLAY_ORDER_SQL =
        "SELECT COALESCE(MAX(display_order), 0) + 1 FROM libraries";

    public static final String GET_NEXT_DISPLAY_ORDER_FALLBACK_SQL =
        "SELECT COALESCE(MAX(id), 0) + 1 FROM libraries";

    public static final String TABLE_INFO_SQL =
        "PRAGMA table_info(%s)";

    public static final String DELETE_LIBRARY_SQL =
        "DELETE FROM libraries WHERE id = ?";

    /**
     * Extract Library from ResultSet.
     */
    public static Library extractLibrary(ResultSet rs) throws SQLException {
        long addedAt = rs.getLong("added_at");
        long lastScanVal = rs.getLong("last_scan");
        Long lastScan = rs.wasNull() ? null : lastScanVal;

        int displayOrder;
        try {
            displayOrder = rs.getInt("display_order");
            if (rs.wasNull()) {
                displayOrder = rs.getInt("id");
            }
        } catch (SQLException e) {
            displayOrder = rs.getInt("id");
        }

        return new Library(
            rs.getInt("id"),
            rs.getString("root_path"),
            rs.getString("name"),
            addedAt,
            lastScan,
            rs.getInt("is_active") == 1,
            displayOrder
        );
    }

    /**
     * Extract ChartMetadata from ResultSet.
     */
    public static ChartMetadata extractMetadata(ResultSet rs) throws SQLException {
        ChartMetadata m = new ChartMetadata();
        m.setId(rs.getInt("id"));
        m.setLibraryId(rs.getInt("library_id"));
        m.setRelativePath(rs.getString("relative_path"));
        m.setSongGroupId(rs.getString("song_group_id"));
        m.setChartListHash(rs.getString("chart_list_hash"));
        m.setSourceFileSize(rs.getLong("source_file_size"));
        m.setSourceFileModified(rs.getLong("source_file_modified"));
        m.setChartType(rs.getString("chart_type"));
        m.setChartIndex(rs.getInt("chart_index"));
        m.setSha256Hash(rs.getString("sha256_hash"));
        m.setTitle(rs.getString("title"));
        m.setArtist(rs.getString("artist"));
        m.setGenre(rs.getString("genre"));
        m.setNoter(rs.getString("noter"));
        m.setLevel(rs.getInt("level"));
        m.setKeys(rs.getInt("keys"));
        m.setPlayers(rs.getInt("players"));
        m.setBpm(rs.getDouble("bpm"));
        m.setNotes(rs.getInt("notes"));
        m.setDuration(rs.getInt("duration"));
        m.setCoverOffset(getIntegerObject(rs, "cover_offset"));
        m.setCoverSize(getIntegerObject(rs, "cover_size"));
        m.setCoverExternalPath(rs.getString("cover_external_path"));
        m.setNoteDataOffset(getIntegerObject(rs, "note_data_offset"));
        m.setNoteDataSize(getIntegerObject(rs, "note_data_size"));
        m.setCachedAt(rs.getLong("cached_at"));
        m.setLibraryRootPath(rs.getString("library_root_path"));
        return m;
    }

    /**
     * Normalize path: convert backslashes to forward slashes, ensure trailing slash.
     */
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        String normalized = path.replace("\\", "/");
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    /**
     * Safely read nullable Integer column from ResultSet.
     * SQLite stores integers as LONG, so we need to handle this carefully.
     */
    private static Integer getIntegerObject(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private ChartDatabaseQueries() {
        // Utility class - no instances
    }
}
