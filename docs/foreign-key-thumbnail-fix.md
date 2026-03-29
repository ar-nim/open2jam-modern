# Foreign Key Constraint Fix for Thumbnail Cache

## Problem

When loading OJN files without embedded thumbnails, the database threw a foreign key constraint error:

```
SEVERE: Exception in chart loader: org.sqlite.SQLiteException: 
[SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed 
(FOREIGN KEY constraint failed)
```

## Root Cause

The database schema has a foreign key relationship:
```sql
FOREIGN KEY (song_group_id) REFERENCES thumbnail(song_group_id) ON DELETE CASCADE
```

The original code in `ChartDatabase.addChartList()` only inserted a thumbnail row when valid thumbnail data existed:

```java
// OLD CODE - Only insert if thumbnail data exists
if (thumbResult.data.length > 0 && thumbnailSize != null) {
    thumbStmt.setString(1, songGroupId);
    // ... set parameters
    thumbStmt.addBatch();
    thumbStmt.executeBatch();
    thumbStmt.clearBatch();
}
```

However, the `chart_cache` row **always** referenced the `song_group_id`, causing a foreign key violation when the thumbnail row didn't exist.

## Solution

**Always insert a thumbnail row**, even when thumbnail data is missing. The thumbnail columns (`thumbnail_data`, `thumbnail_size`, `cover_offset`, `cover_size`) can contain `NULL` values.

### Changes Made

#### 1. Updated Schema (line ~139)
```sql
CREATE TABLE IF NOT EXISTS thumbnail (
    song_group_id TEXT PRIMARY KEY,
    cover_offset INTEGER,                 -- NULL if no cover
    cover_size INTEGER,                   -- NULL if no cover
    thumbnail_data BLOB,                  -- NULL if not extractable
    thumbnail_size INTEGER,               -- NULL if not extractable
    cached_at INTEGER NOT NULL
);
```

#### 2. Updated Insertion Logic (line ~743)
```java
// NEW CODE - Always insert a row, even if thumbnail data is missing
thumbStmt.setString(1, songGroupId);
thumbStmt.setObject(2, coverOffset);
thumbStmt.setObject(3, coverSize);
thumbStmt.setBytes(4, thumbResult.data.length > 0 ? thumbResult.data : null);
thumbStmt.setObject(5, thumbnailSize);
thumbStmt.setLong(6, System.currentTimeMillis());
thumbStmt.addBatch();
thumbStmt.executeBatch();
thumbStmt.clearBatch();
```

## Impact

- ✅ OJN files without thumbnails can now be cached successfully
- ✅ Foreign key constraint is satisfied (thumbnail row always exists)
- ✅ NULL values in thumbnail columns indicate missing thumbnail data
- ✅ Existing charts with thumbnails are unaffected
- ✅ Backward compatible - existing database will work after rebuild

## Migration

For existing databases, users need to:
1. Delete the old `save/songcache.db` file, OR
2. Re-scan their library (the schema is compatible, just needs new rows)

The fix ensures that every `song_group_id` in `chart_cache` has a corresponding row in the `thumbnail` table, even if that row contains NULL values for the thumbnail data.

## Testing

1. Load a folder with OJN files that have no embedded thumbnails
2. Verify no SQLiteException is thrown
3. Verify charts appear in the song selection list
4. Verify charts without thumbnails display a placeholder (or default cover)

## Files Modified

- `src/org/open2jam/persistence/ChartDatabase.java`
  - Line ~139: Updated thumbnail table schema comments
  - Line ~743: Changed thumbnail insertion logic to always insert a row

## Related

- Issue: Foreign key constraint failed when loading OJN files without thumbnails
- Fix: Always insert thumbnail row with NULL data when thumbnail is missing
- Commit: [to be added]
