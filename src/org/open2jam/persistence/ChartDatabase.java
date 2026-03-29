package org.open2jam.persistence;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.open2jam.parsers.Chart;
import org.open2jam.parsers.ChartList;
import org.open2jam.parsers.ChartMetadata;
import org.open2jam.parsers.ChartParser;
import org.open2jam.parsers.OJNChart;
import org.open2jam.parsers.SHA256Util;
import org.open2jam.parsers.utils.Logger;
import org.open2jam.util.DebugLogger;

/**
 * SQLite-based chart metadata cache with root-relative path model.
 */
public class ChartDatabase {

    private static final boolean DEBUG = Boolean.getBoolean("open2jam.debug");
    private static Connection writerConnection;
    private static final ReentrantLock writeLock = new ReentrantLock();
    private static final String DB_PATH = "save/songcache.db";

    private static final ExecutorService hashExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChartCache-HashCalculator");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean shuttingDown = false;

    private static PreparedStatement getMetadataByPathStmt = null;

    private ChartDatabase() {
    }

    private static Connection createReadConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    public static void initialize() {
        File saveDir = new File("save");
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            throw new IllegalStateException("Failed to create save directory: " + saveDir.getAbsolutePath());
        }

        try {
            File dbFile = new File(DB_PATH);
            writerConnection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = writerConnection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.executeUpdate(ChartDatabaseQueries.CREATE_TABLES_SQL);
            }

            migrateSchema();

        } catch (SQLException e) {
            throw new IllegalStateException("ChartDatabase initialization failed", e);
        }
    }

    private static void migrateSchema() throws SQLException {
        writeLock.lock();
        try {
            boolean hasDisplayOrder = false;
            try (PreparedStatement stmt = writerConnection.prepareStatement(
                    String.format(ChartDatabaseQueries.TABLE_INFO_SQL, "libraries"));
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if ("display_order".equals(rs.getString("name"))) {
                        hasDisplayOrder = true;
                        break;
                    }
                }
            }

            if (!hasDisplayOrder) {
                DebugLogger.debug("Schema migration: Adding display_order column to libraries table");
                writerConnection.setAutoCommit(false);
                try {
                    try (Statement stmt = writerConnection.createStatement()) {
                        stmt.execute("ALTER TABLE libraries ADD COLUMN display_order INTEGER");
                    }

                    try (Statement stmt = writerConnection.createStatement()) {
                        stmt.execute("UPDATE libraries SET display_order = id");
                    }

                    writerConnection.commit();
                    DebugLogger.debug("Schema migration completed: display_order column added");
                } catch (SQLException e) {
                    writerConnection.rollback();
                    DebugLogger.debug("Schema migration rolled back");
                    throw e;
                } finally {
                    writerConnection.setAutoCommit(true);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public static void close() {
        shuttingDown = true;

        hashExecutor.shutdown();
        try {
            if (!hashExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                hashExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            hashExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (getMetadataByPathStmt != null) {
            try {
                if (!getMetadataByPathStmt.isClosed()) {
                    getMetadataByPathStmt.close();
                }
            } catch (SQLException e) {
                Logger.global.log(Level.WARNING, "Failed to close prepared statement", e);
            }
            getMetadataByPathStmt = null;
        }

        if (writerConnection != null) {
            try {
                if (!writerConnection.isClosed()) {
                    writerConnection.close();
                }
            } catch (SQLException e) {
                Logger.global.log(Level.WARNING, "Failed to close writer connection", e);
            }
            writerConnection = null;
        }
    }

    public static void beginBulkInsert() throws SQLException {
        writeLock.lock();
        try {
            writerConnection.setAutoCommit(false);
        } catch (SQLException e) {
            writeLock.unlock();
            throw e;
        }
    }

    public static void commitBulkInsert() throws SQLException {
        try {
            writerConnection.commit();
            writerConnection.setAutoCommit(true);
        } finally {
            writeLock.unlock();
        }
    }

    public static void rollbackBulkInsert() throws SQLException {
        if (writeLock.isHeldByCurrentThread()) {
            try {
                writerConnection.rollback();
                writerConnection.setAutoCommit(true);
            } finally {
                writeLock.unlock();
            }
        }
    }

    public static Library addLibrary(String rootPath, String name) throws SQLException {
        String normalizedPath = ChartDatabaseQueries.normalizePath(rootPath);

        writeLock.lock();
        try {
            Library existing = getLibraryByPath(normalizedPath);
            if (existing != null) {
                return existing;
            }

            int nextDisplayOrder = getNextDisplayOrder();

            try (PreparedStatement stmt = writerConnection.prepareStatement(
                    "INSERT INTO libraries (root_path, name, added_at, display_order, is_active) " +
                    "VALUES (?, ?, ?, ?, 1)")) {

                stmt.setString(1, normalizedPath);
                stmt.setString(2, name);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setInt(4, nextDisplayOrder);
                stmt.executeUpdate();
            }

            return getLibraryByPath(normalizedPath);
        } finally {
            writeLock.unlock();
        }
    }

    private static int getNextDisplayOrder() throws SQLException {
        boolean hasDisplayOrder = columnExists("libraries", "display_order");

        if (!hasDisplayOrder) {
            DebugLogger.debug("getNextDisplayOrder: display_order column not found, using id-based ordering");
            try (PreparedStatement stmt = writerConnection.prepareStatement(
                    ChartDatabaseQueries.GET_NEXT_DISPLAY_ORDER_FALLBACK_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return 1;
        }

        try (PreparedStatement stmt = writerConnection.prepareStatement(
                ChartDatabaseQueries.GET_NEXT_DISPLAY_ORDER_SQL)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int nextOrder = rs.getInt(1);
                    DebugLogger.debug("getNextDisplayOrder: Next display_order = " + nextOrder);
                    return nextOrder;
                }
            }
        }
        return 1;
    }

    private static boolean columnExists(String tableName, String columnName) throws SQLException {
        try (PreparedStatement stmt = writerConnection.prepareStatement(
                String.format(ChartDatabaseQueries.TABLE_INFO_SQL, tableName));
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Library getLibraryById(int id) throws SQLException {
        try (Connection conn = createReadConnection();
             PreparedStatement stmt = conn.prepareStatement(ChartDatabaseQueries.GET_LIBRARY_BY_ID_SQL)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return ChartDatabaseQueries.extractLibrary(rs);
                }
            }
            return null;
        }
    }

    private static Library getLibraryByPath(String rootPath) throws SQLException {
        try (Connection conn = createReadConnection();
             PreparedStatement stmt = conn.prepareStatement(ChartDatabaseQueries.GET_LIBRARY_BY_PATH_SQL)) {

            stmt.setString(1, rootPath);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return ChartDatabaseQueries.extractLibrary(rs);
                }
            }
            return null;
        }
    }

    public static List<Library> getAllLibraries() throws SQLException {
        List<Library> libraries = new ArrayList<>();

        boolean hasDisplayOrder = columnExists("libraries", "display_order");

        String orderBy = hasDisplayOrder ? "display_order ASC" : "id ASC";
        String sql = "SELECT id, root_path, name, added_at, last_scan, is_active" +
                     (hasDisplayOrder ? ", display_order" : "") +
                     " FROM libraries ORDER BY " + orderBy;

        try (Connection conn = createReadConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    libraries.add(ChartDatabaseQueries.extractLibrary(rs));
                }
            }
        }

        return libraries;
    }

    public static void updateLibraryRoot(int id, String newRootPath) throws SQLException {
        String normalizedPath = ChartDatabaseQueries.normalizePath(newRootPath);

        writeLock.lock();
        try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.UPDATE_LIBRARY_ROOT_SQL)) {
            stmt.setString(1, normalizedPath);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } finally {
            writeLock.unlock();
        }
    }

    public static void updateLibraryScanTime(int id) throws SQLException {
        writeLock.lock();
        try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.UPDATE_LIBRARY_SCAN_TIME_SQL)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } finally {
            writeLock.unlock();
        }
    }

    public static class BatchInserter implements AutoCloseable {
        private final PreparedStatement stmt;
        private final PreparedStatement thumbStmt;
        private int batchSize = 0;
        private static final int BATCH_THRESHOLD = 1000;

        private static final ThreadLocal<MessageDigest> SHA1_DIGEST = ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-1 algorithm not available", e);
            }
        });

        public BatchInserter() throws SQLException {
            this.stmt = writerConnection.prepareStatement(ChartDatabaseQueries.INSERT_CHART_SQL);
            this.thumbStmt = writerConnection.prepareStatement(ChartDatabaseQueries.INSERT_THUMBNAIL_SQL);
        }

        public void addChartList(Library library, ChartList chartList) throws SQLException {
            File sourceFile = chartList.getSource();
            String absolutePath = sourceFile.getAbsolutePath().replace("\\", "/");

            if (!absolutePath.startsWith(library.rootPath)) {
                throw new IllegalArgumentException(
                    "Chart file " + absolutePath + " is not under library root " + library.rootPath);
            }
            String relativePath = absolutePath.substring(library.rootPath.length());

            long fileSize = sourceFile.length();
            long fileModified = sourceFile.lastModified();
            String songGroupId = generateSongGroupId(library.id, relativePath);
            String chartListHash = generateChartListHash(relativePath, fileModified);

            Integer thumbnailSize = null;
            Integer coverOffset = null;
            Integer coverSize = null;

            if (!chartList.isEmpty() && chartList.get(0) instanceof OJNChart ojn) {
                coverOffset = ojn.getCoverOffset();
                coverSize = ojn.getCoverSize();

                ThumbnailResult thumbResult = cacheThumbnail(sourceFile, coverOffset, coverSize);
                thumbnailSize = thumbResult.size;

                thumbStmt.setString(1, songGroupId);
                thumbStmt.setObject(2, coverOffset);
                thumbStmt.setObject(3, coverSize);
                thumbStmt.setBytes(4, thumbResult.data.length > 0 ? thumbResult.data : null);
                thumbStmt.setObject(5, thumbnailSize);
                thumbStmt.setLong(6, System.currentTimeMillis());
                thumbStmt.addBatch();
                thumbStmt.executeBatch();
                thumbStmt.clearBatch();
            }

            for (int i = 0; i < chartList.size(); i++) {
                Chart chart = chartList.get(i);

                Integer noteOffset = null;
                Integer noteSize = null;
                String coverExternalPath = null;

                if (chart instanceof OJNChart ojn) {
                    noteOffset = ojn.getNoteOffset();
                    noteSize = ojn.getNoteOffsetEnd() - ojn.getNoteOffset();
                } else if (chart.hasCover()) {
                    coverExternalPath = chart.getCoverName();
                }

                int paramIndex = 1;
                stmt.setInt(paramIndex++, library.id);
                stmt.setString(paramIndex++, relativePath);
                stmt.setString(paramIndex++, songGroupId);
                stmt.setString(paramIndex++, chartListHash);
                stmt.setLong(paramIndex++, fileSize);
                stmt.setLong(paramIndex++, fileModified);
                stmt.setString(paramIndex++, chart.getType() != null ? chart.getType().name() : "NONE");
                stmt.setInt(paramIndex++, i);
                stmt.setString(paramIndex++, chart.getTitle());
                stmt.setString(paramIndex++, chart.getArtist());
                stmt.setString(paramIndex++, chart.getGenre());
                stmt.setString(paramIndex++, chart.getNoter());
                stmt.setInt(paramIndex++, chart.getLevel());
                stmt.setInt(paramIndex++, chart.getKeys());
                stmt.setInt(paramIndex++, chart.getPlayers());
                stmt.setDouble(paramIndex++, chart.getBPM());
                stmt.setInt(paramIndex++, chart.getNoteCount());
                stmt.setInt(paramIndex++, chart.getDuration());
                stmt.setObject(paramIndex++, noteOffset);
                stmt.setObject(paramIndex++, noteSize);
                stmt.setString(paramIndex++, coverExternalPath);
                stmt.setLong(paramIndex, System.currentTimeMillis());

                stmt.addBatch();
                batchSize++;

                if (batchSize >= BATCH_THRESHOLD) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                    batchSize = 0;
                }
            }
        }

        public void flush() throws SQLException {
            if (batchSize > 0) {
                stmt.executeBatch();
                stmt.clearBatch();
            }
        }

        @Override
        public void close() throws SQLException {
            stmt.close();
            thumbStmt.close();
        }

        private String generateSongGroupId(int libraryId, String relativePath) {
            String input = libraryId + ":" + relativePath;
            MessageDigest md = SHA1_DIGEST.get();
            md.reset();
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        }

        private String generateChartListHash(String relativePath, long modified) {
            return Integer.toHexString((relativePath + modified).hashCode());
        }

        private static String bytesToHex(byte[] bytes) {
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }

        private static record ThumbnailResult(byte[] data, Integer size) {}

        private static ThumbnailResult cacheThumbnail(File sourceFile, Integer coverOffset, Integer coverSize) {
            if (coverOffset == null || coverSize == null || coverOffset <= 0 || coverSize <= 0) {
                return new ThumbnailResult(new byte[0], null);
            }

            long fileLen = sourceFile.length();
            int thumbnailOffset = coverOffset + coverSize;

            if (thumbnailOffset <= 0 || thumbnailOffset >= fileLen - 1) {
                return new ThumbnailResult(new byte[0], null);
            }

            try {
                int[] thumbSizeRef = new int[1];
                byte[] thumbnailData = readThumbnailFromOJN(sourceFile, thumbnailOffset, thumbSizeRef);
                if (thumbnailData.length > 0 && thumbSizeRef[0] > 0 && thumbSizeRef[0] < 1_000_000) {
                    return new ThumbnailResult(thumbnailData, thumbSizeRef[0]);
                }
            } catch (Exception e) {
            }

            return new ThumbnailResult(new byte[0], null);
        }

        private static byte[] readThumbnailFromOJN(File sourceFile, int thumbnailOffset, int[] thumbnailSizeRef) {
            try (RandomAccessFile f = new RandomAccessFile(sourceFile, "r")) {
                long fileLen = f.length();
                if (thumbnailOffset <= 0 || thumbnailOffset >= fileLen - 1) {
                    return new byte[0];
                }

                f.seek(thumbnailOffset);
                byte[] header = new byte[2];
                if (f.read(header) != 2) {
                    return new byte[0];
                }

                int thumbnailSize;
                f.seek(thumbnailOffset);

                if ((header[0] & 0xFF) == 0x42 && (header[1] & 0xFF) == 0x4D) {
                    thumbnailSize = findBmpSize(f, thumbnailOffset);
                }
                else if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8) {
                    thumbnailSize = findJpegSize(f, thumbnailOffset);
                }
                else if ((header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50) {
                    thumbnailSize = findPngSize(f, thumbnailOffset);
                }
                else {
                    return new byte[0];
                }

                if (thumbnailSize <= 0 || thumbnailSize > 1_000_000 || thumbnailOffset + thumbnailSize > fileLen) {
                    return new byte[0];
                }

                f.seek(thumbnailOffset);
                byte[] thumbnailData = new byte[thumbnailSize];
                f.readFully(thumbnailData);

                if (thumbnailSizeRef != null && thumbnailSizeRef.length > 0) {
                    thumbnailSizeRef[0] = thumbnailSize;
                }

                return thumbnailData;
            } catch (IOException e) {
                return new byte[0];
            }
        }

        private static int findBmpSize(RandomAccessFile f, long startOffset) throws IOException {
            f.seek(startOffset + 2);
            byte[] sizeBytes = new byte[4];
            if (f.read(sizeBytes) != 4) return -1;
            return (sizeBytes[0] & 0xFF) |
                   ((sizeBytes[1] & 0xFF) << 8) |
                   ((sizeBytes[2] & 0xFF) << 16) |
                   ((sizeBytes[3] & 0xFF) << 24);
        }

        private static int findJpegSize(RandomAccessFile f, long startOffset) throws IOException {
            f.seek(startOffset);
            int size = 2;
            byte prev = 0;
            while (true) {
                int b = f.read();
                if (b < 0) break;
                size++;
                if (prev == (byte)0xFF && b == 0xD9) return size;
                if (size > 2_000_000) return size;
                prev = (byte) b;
            }
            return size;
        }

        private static int findPngSize(RandomAccessFile f, long startOffset) throws IOException {
            f.seek(startOffset);
            byte[] header = new byte[8];
            if (f.read(header) != 8) return -1;
            int size = 8;
            byte[] chunkHeader = new byte[8];
            while (true) {
                if (f.read(chunkHeader) != 8) break;
                int chunkLength = ((chunkHeader[0] & 0xFF) << 24) |
                                  ((chunkHeader[1] & 0xFF) << 16) |
                                  ((chunkHeader[2] & 0xFF) << 8) |
                                  (chunkHeader[3] & 0xFF);
                size += 8 + chunkLength + 4;
                if (chunkHeader[4] == 'I' && chunkHeader[5] == 'E' &&
                    chunkHeader[6] == 'N' && chunkHeader[7] == 'D') return size;
                f.skipBytes(chunkLength + 4);
                if (size > 2_000_000) return size;
            }
            return size;
        }
    }

    public static List<ChartMetadata> getCachedCharts(int libraryId) {
        try (Connection conn = createReadConnection();
             PreparedStatement stmt = conn.prepareStatement(ChartDatabaseQueries.GET_CACHED_CHARTS_SQL)) {

            stmt.setInt(1, libraryId);
            List<ChartMetadata> results = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChartMetadata m = ChartDatabaseQueries.extractMetadata(rs);
                    results.add(m);
                }
                return results;
            }
        } catch (SQLException e) {
            Logger.global.log(Level.SEVERE, e, () -> "Cache read error for library " + libraryId);
            return Collections.emptyList();
        }
    }

    public static List<SongGroup> getSongGroups(int libraryId) {
        try (Connection conn = createReadConnection();
             PreparedStatement stmt = conn.prepareStatement(ChartDatabaseQueries.GET_SONG_GROUPS_SQL)) {

            stmt.setInt(1, libraryId);
            List<SongGroup> groups = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    groups.add(new SongGroup(
                        rs.getString("song_group_id"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getInt("diff_count"),
                        rs.getInt("min_level"),
                        rs.getInt("max_level")
                    ));
                }
                return groups;
            }
        } catch (SQLException e) {
            Logger.global.log(Level.SEVERE, e, () -> "Failed to get song groups for library " + libraryId);
            return Collections.emptyList();
        }
    }

    public static List<ChartMetadata> getDifficultiesForSong(String songGroupId) {
        try (Connection conn = createReadConnection();
             PreparedStatement stmt = conn.prepareStatement(ChartDatabaseQueries.GET_DIFFICULTIES_FOR_SONG_SQL)) {

            stmt.setString(1, songGroupId);
            List<ChartMetadata> diffs = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChartMetadata m = ChartDatabaseQueries.extractMetadata(rs);
                    diffs.add(m);
                }
                return diffs;
            }
        } catch (SQLException e) {
            Logger.global.log(Level.SEVERE, e, () -> "Failed to get difficulties for song " + songGroupId);
            return Collections.emptyList();
        }
    }

    public static Chart loadChartForPlay(ChartMetadata cached) {
        String fullPath = cached.getFullPath();
        File sourceFile = new File(fullPath);

        if (!sourceFile.exists()) {
            Logger.global.warning(() -> String.format("Chart file missing: %s - invalidating cache", fullPath));
            invalidateCache(cached.getId());
            return null;
        }

        long currentModified = sourceFile.lastModified();
        if (currentModified != cached.getSourceFileModified()) {
            return handleModifiedChart(cached, sourceFile);
        }

        return parseAndReturnChart(sourceFile, cached.getChartIndex());
    }

    private static Chart handleModifiedChart(ChartMetadata cached, File sourceFile) {
        invalidateCache(cached.getId());

        ChartList newList = org.open2jam.parsers.ChartParser.parseFile(sourceFile);
        if (newList.isEmpty()) {
            return null;
        }

        try {
            Library lib = getLibraryById(cached.getLibraryId());
            if (lib != null) {
                try (BatchInserter batch = new BatchInserter()) {
                    batch.addChartList(lib, newList);
                    batch.flush();
                }
            }
        } catch (SQLException e) {
            Logger.global.log(Level.WARNING, "Failed to re-cache modified chart", e);
        }

        return getChartByIndex(newList, cached.getChartIndex());
    }

    private static Chart parseAndReturnChart(File sourceFile, int chartIndex) {
        ChartList chartList = org.open2jam.parsers.ChartParser.parseFile(sourceFile);
        if (chartList.isEmpty()) {
            return null;
        }
        return getChartByIndex(chartList, chartIndex);
    }

    private static Chart getChartByIndex(ChartList chartList, int chartIndex) {
        if (chartIndex >= 0 && chartIndex < chartList.size()) {
            return chartList.get(chartIndex);
        }
        return !chartList.isEmpty() ? chartList.get(0) : null;
    }

    public static BufferedImage getCoverFromCache(ChartMetadata cached) {
        BufferedImage cover = getCoverFromBlob(cached);
        if (cover != null) return cover;

        cover = getEmbeddedCover(cached);
        if (cover != null) return cover;

        return getExternalCover(cached);
    }

    private static BufferedImage getCoverFromBlob(ChartMetadata cached) {
        if (cached.getThumbnailData() == null || cached.getThumbnailData().length == 0) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(cached.getThumbnailData()));
            if (DEBUG) {
                Logger.global.info(() -> String.format("[DEBUG] getCoverFromBlob: Using cached BLOB thumbnail (%d bytes, %dx%d)",
                    cached.getThumbnailData().length, img.getWidth(), img.getHeight()));
            }
            return img;
        } catch (IOException e) {
            if (DEBUG) {
                Logger.global.info(() -> "[DEBUG] getCoverFromBlob: Failed to read BLOB, falling back");
            }
            return null;
        }
    }

    private static BufferedImage getEmbeddedCover(ChartMetadata cached) {
        if (!cached.hasEmbeddedCover()) {
            return null;
        }

        Integer coverOffset = cached.getCoverOffset();
        Integer coverSize = cached.getCoverSize();

        if (coverSize == null || coverSize <= 0 || coverSize > 10_000_000) {
            Logger.global.warning(() -> String.format("Invalid cover size for %s: %d", cached.getRelativePath(), coverSize));
            return null;
        }

        if (coverOffset == null || coverOffset <= 0) {
            Logger.global.warning(() -> String.format("Invalid cover offset for %s: %d", cached.getRelativePath(), coverOffset));
            return null;
        }

        try (RandomAccessFile f = new RandomAccessFile(cached.getFullPath(), "r")) {
            f.seek(coverOffset);
            byte[] coverBytes = new byte[coverSize];
            f.readFully(coverBytes);
            if (DEBUG) {
                Logger.global.info(() -> String.format("[DEBUG] getEmbeddedCover: Reading full cover from file (%d bytes)", coverSize));
            }
            return ImageIO.read(new ByteArrayInputStream(coverBytes));
        } catch (IOException e) {
            Logger.global.log(Level.WARNING, e, () -> "Failed to read embedded cover from " + cached.getRelativePath());
            return null;
        }
    }

    private static BufferedImage getExternalCover(ChartMetadata cached) {
        String coverPath = cached.getCoverExternalPath();
        if (coverPath == null || coverPath.isEmpty()) {
            return null;
        }
        try {
            return ImageIO.read(new File(coverPath));
        } catch (IOException e) {
            Logger.global.log(Level.WARNING, e, () -> "Failed to read external cover " + coverPath);
            return null;
        }
    }

    public static BufferedImage getCoverForChart(Chart chart) {
        if (chart == null || chart.getSource() == null) {
            return null;
        }

        File sourceFile = chart.getSource();
        String sourcePath = sourceFile.getAbsolutePath().replace("\\", "/");

        try {
            List<Library> libs = getAllLibraries();
            for (Library lib : libs) {
                if (sourcePath.startsWith(lib.rootPath)) {
                    String relativePath = sourcePath.substring(lib.rootPath.length());
                    ChartMetadata metadata = getMetadataByPath(lib.id, relativePath);
                    if (metadata != null) {
                        return getCoverFromCache(metadata);
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            Logger.global.log(Level.WARNING, e, () -> "Failed to get cached cover for " + sourceFile.getName());
        }

        return chart.getCover();
    }

    public static BufferedImage getFullSizeCoverForChart(Chart chart) {
        if (chart == null || chart.getSource() == null) {
            return null;
        }

        File sourceFile = chart.getSource();
        String sourcePath = sourceFile.getAbsolutePath().replace("\\", "/");

        try {
            List<Library> libs = getAllLibraries();
            for (Library lib : libs) {
                if (sourcePath.startsWith(lib.rootPath)) {
                    String relativePath = sourcePath.substring(lib.rootPath.length());
                    ChartMetadata metadata = getMetadataByPath(lib.id, relativePath);
                    if (metadata != null) {
                        return getFullSizeCoverFromCache(metadata);
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            Logger.global.log(Level.WARNING, e, () -> "Failed to get cached cover for " + sourceFile.getName());
        }

        return chart.getCover();
    }

    public static BufferedImage getFullSizeCoverFromCache(ChartMetadata cached) {
        if (cached.hasEmbeddedCover()) {
            Integer coverOffset = cached.getCoverOffset();
            Integer coverSize = cached.getCoverSize();

            if (coverSize <= 0 || coverSize > 10_000_000) {
                Logger.global.warning(() -> String.format("Invalid cover size for %s: %d", cached.getRelativePath(), coverSize));
                return null;
            }

            try (RandomAccessFile f = new RandomAccessFile(cached.getFullPath(), "r")) {
                f.seek(coverOffset);
                byte[] coverBytes = new byte[coverSize];
                f.readFully(coverBytes);
                return ImageIO.read(new ByteArrayInputStream(coverBytes));
            } catch (IOException e) {
                Logger.global.log(Level.WARNING, e, () -> "Failed to read embedded cover from " + cached.getRelativePath());
            }
        }

        if (cached.getCoverExternalPath() != null && !cached.getCoverExternalPath().isEmpty()) {
            try {
                return ImageIO.read(new File(cached.getCoverExternalPath()));
            } catch (IOException e) {
                Logger.global.log(Level.WARNING, e, () -> "Failed to read external cover " + cached.getCoverExternalPath());
            }
        }

        return null;
    }

    private static ChartMetadata getMetadataByPath(int libraryId, String relativePath) throws SQLException {
        if (getMetadataByPathStmt == null || getMetadataByPathStmt.isClosed()) {
            getMetadataByPathStmt = writerConnection.prepareStatement(ChartDatabaseQueries.GET_METADATA_BY_PATH_SQL);
        }

        getMetadataByPathStmt.setInt(1, libraryId);
        getMetadataByPathStmt.setString(2, relativePath);

        try (ResultSet rs = getMetadataByPathStmt.executeQuery()) {
            if (rs.next()) {
                return ChartDatabaseQueries.extractMetadata(rs);
            }
        }
        return null;
    }

    public static String getOrCalculateHash(ChartMetadata cached) {
        if (cached.getSha256Hash() != null) {
            return cached.getSha256Hash();
        }

        calculateHashAsync(cached);
        return null;
    }

    public static String getHashForScore(ChartMetadata cached) {
        if (cached.getSha256Hash() != null) {
            return cached.getSha256Hash();
        }

        ChartList chartList = ChartParser.parseFile(
            new File(cached.getFullPath())
        );

        if (chartList.isEmpty()) {
            return null;
        }

        Chart chart = (cached.getChartIndex() >= 0 && cached.getChartIndex() < chartList.size())
            ? chartList.get(cached.getChartIndex())
            : chartList.get(0);

        String hash = SHA256Util.hashChart(chart);
        updateHash(cached.getId(), hash);
        return hash;
    }

    private static void calculateHashAsync(ChartMetadata cached) {
        if (shuttingDown) {
            return;
        }

        hashExecutor.submit(() -> {
            if (shuttingDown) {
                return;
            }

            try {
                ChartList chartList = ChartParser.parseFile(
                    new File(cached.getFullPath())
                );

                if (chartList.isEmpty()) {
                    return;
                }

                Chart chart = (cached.getChartIndex() >= 0 && cached.getChartIndex() < chartList.size())
                    ? chartList.get(cached.getChartIndex())
                    : chartList.get(0);

                String hash = SHA256Util.hashChart(chart);
                if (hash != null) {
                    updateHash(cached.getId(), hash);
                }
            } catch (Exception e) {
                Logger.global.log(Level.WARNING, e, () -> "Failed to calculate hash for " + cached.getRelativePath());
            }
        });
    }

    private static void updateHash(int chartId, String hash) {
        writeLock.lock();
        try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.UPDATE_HASH_SQL)) {
            stmt.setString(1, hash);
            stmt.setInt(2, chartId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.global.log(Level.WARNING, e, () -> "Failed to update SHA-256 hash for chart " + chartId);
        } finally {
            writeLock.unlock();
        }
    }

    private static void invalidateCache(int chartId) {
        writeLock.lock();
        try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.DELETE_CHART_SQL)) {
            stmt.setInt(1, chartId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.global.log(Level.WARNING, e, () -> "Failed to invalidate cache for chart " + chartId);
        } finally {
            writeLock.unlock();
        }
    }

    public static void deleteCacheForLibrary(int libraryId) throws SQLException {
        writeLock.lock();
        try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.DELETE_CHARTS_FOR_LIBRARY_SQL)) {
            stmt.setInt(1, libraryId);
            stmt.executeUpdate();
        } finally {
            writeLock.unlock();
        }
    }

    public static void deleteLibrary(int libraryId) throws SQLException {
        writeLock.lock();
        try {
            writerConnection.setAutoCommit(false);
            try {
                try (Statement stmt = writerConnection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF");
                }

                try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.DELETE_CHARTS_FOR_LIBRARY_SQL)) {
                    stmt.setInt(1, libraryId);
                    int chartsDeleted = stmt.executeUpdate();
                    DebugLogger.debug("Deleted " + chartsDeleted + " charts for library id=" + libraryId);
                }

                try (PreparedStatement stmt = writerConnection.prepareStatement(
                        ChartDatabaseQueries.DELETE_ORPHANED_THUMBNAILS_SQL)) {
                    int orphansDeleted = stmt.executeUpdate();
                    if (orphansDeleted > 0) {
                        DebugLogger.debug("Cleaned up " + orphansDeleted + " orphaned thumbnails");
                    }
                }

                try (PreparedStatement stmt = writerConnection.prepareStatement(ChartDatabaseQueries.DELETE_LIBRARY_SQL)) {
                    stmt.setInt(1, libraryId);
                    int libsDeleted = stmt.executeUpdate();
                    DebugLogger.debug("Deleted " + libsDeleted + " library entry (id=" + libraryId + ")");
                }

                writerConnection.commit();
                DebugLogger.debug("Library deletion committed (id=" + libraryId + ")");
            } catch (SQLException e) {
                writerConnection.rollback();
                DebugLogger.debug("Library deletion rolled back (id=" + libraryId + ")");
                throw e;
            } finally {
                try (Statement stmt = writerConnection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
                writerConnection.setAutoCommit(true);
            }
        } finally {
            writeLock.unlock();
        }
    }
}
