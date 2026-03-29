package org.open2jam.persistence;

/**
 * Data Transfer Object for library root information.
 * Represents a root directory containing chart files.
 *
 * Thread Safety: This class is immutable and therefore thread-safe.
 *
 * @author open2jam-modern team
 */
public class Library {
    public final int id;
    public final String rootPath;        // Absolute path (forward slashes only)
    public final String name;            // User-friendly name
    public final long addedAt;           // Unix timestamp (milliseconds)
    public final Long lastScan;          // Null if never scanned
    public final boolean isActive;       // False = disabled
    public final int displayOrder;       // UI sort order (sparse, gap-tolerant)

    /**
     * Construct a Library DTO.
     *
     * @param id Database row ID
     * @param rootPath Absolute path to library root (must use forward slashes)
     * @param name User-friendly name for the library
     * @param addedAt Unix timestamp when library was added
     * @param lastScan Unix timestamp of last successful scan, or null if never scanned
     * @param isActive Whether the library is enabled
     * @param displayOrder UI sort order (sparse sequence, gaps acceptable)
     */
    public Library(int id, String rootPath, String name, long addedAt,
                   Long lastScan, boolean isActive, int displayOrder) {
        this.id = id;
        this.rootPath = rootPath;
        this.name = name;
        this.addedAt = addedAt;
        this.lastScan = lastScan;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
    }

    /**
     * Get full absolute path for a relative chart path.
     *
     * <p>Security Note: This method does NOT validate the relativePath parameter.
     * Callers must ensure relativePath does not contain ".." or other path traversal
     * sequences to prevent accessing files outside the library root.</p>
     *
     * @param relativePath Path relative to library root (must use forward slashes)
     * @return Full absolute path: rootPath + "/" + relativePath
     * @throws IllegalStateException if relativePath is null
     */
    public String getFullPath(String relativePath) {
        if (relativePath == null) {
            throw new IllegalStateException("relativePath cannot be null");
        }
        // Security: Check for path traversal attempts
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("Path traversal not allowed: " + relativePath);
        }
        return rootPath + "/" + relativePath;
    }

    /**
     * Check if this library has been scanned at least once.
     *
     * @return true if lastScan is not null
     */
    public boolean hasBeenScanned() {
        return lastScan != null;
    }

    @Override
    public String toString() {
        return "Library{" +
               "id=" + id +
               ", rootPath='" + rootPath + '\'' +
               ", name='" + name + '\'' +
               ", isActive=" + isActive +
               ", displayOrder=" + displayOrder +
               '}';
    }
}
