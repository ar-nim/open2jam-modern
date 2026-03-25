# Removal Plan Summary - O2Jam-Only Modernization

**Date:** March 2026  
**Status:** Ready for Implementation

---

## Key Corrections Applied

### 1. OJM is NOT a Separate Format ✅
- **OJM files are embedded within OJN files** (audio data container)
- **Chart.TYPE enum** will only have `{NONE, OJN}`
- **OJNChart.getSamples()** calls `OJMParser.parseFile()` internally
- OJMParser remains as the audio extraction utility

### 2. Keysound Extractor Replaces Format Conversion ✅
- **Remove:** "Convert to BMS/SM/SNP" context menu items
- **Add:** "Extract Keysounds" menu item
- **Functionality:** Right-click song → Extract keysounds to folder
- **Implementation:** Uses existing `Chart.copySampleFiles()` method

### 3. Voile Already Removed ✅
- **Config.java** already uses Jackson JSON (no code changes needed)
- **Only remove:** Comment about VoileMap migration
- **Safe to remove:** `lib/voile.jar` dependency
- **No migration required:** config.vl → config.json already done

---

## Updated Removal Summary

### Files to Remove (14 total)

**Parsers (11 files):**
```
parsers/src/org/open2jam/parsers/
├── BMSChart.java
├── BMSParser.java
├── BMSWriter.java          ← Removed (was BMS export)
├── SMChart.java
├── SMParser.java
├── SNPParser.java
├── XNTChart.java
├── XNTParser.java
├── utils/KrazyRainDB.java
└── utils/CharsetDetector.java
```

**Main Source (3 files):**
```
src/org/open2jam/
├── util/DJMaxDBLoader.java
src/resources/
├── DJMAX_ONLINE.csv
└── DJMAX_ONLINE.ods
```

**Resources (1 file):**
```
src/resources/KrazyRain.xml
```

### Dependencies to Remove (7 JARs)

```gradle
// build.gradle - REMOVE:
implementation "net.java.dev.jna:jna:${jnaVersion}"
implementation "net.java.dev.jna:jna-platform:${jnaVersion}"
implementation "uk.co.caprica:vlcj:${vlcjVersion}"
implementation files('lib/voile.jar')  // NOT USED

// parsers/build.gradle - REMOVE:
implementation files('../lib/chardet.jar')
```

**Remove from lib/ folder:**
```
chardet.jar          # BMS/SM charset detection
vlcj-*.jar           # Video playback (unused)
jna-*.jar            # VLCJ dependency
platform-*.jar       # VLCJ dependency
voile.jar            # NOT USED (Config uses JSON)
lzma.jar             # UNUSED
lwjgl-2.9.3.jar      # Legacy LWJGL 2
lwjgl_util-2.9.3.jar # Legacy LWJGL 2
```

**Keep:**
```
partytime.jar        # LAN multiplayer
```

---

## Code Changes Required

### Minimal Changes (5 files)

| File | Change Type | Description |
|------|-------------|-------------|
| `Chart.java` | Modify | Change TYPE enum to `{NONE, OJN}` |
| `ChartParser.java` | Simplify | OJN-only detection |
| `Render.java` | Remove | BMS timing + video code |
| `MusicSelection.java` | Replace | BMS conversion → Keysound extractor |
| `Configuration.java` | Remove | VLC path panel |
| `GameOptions.java` | Remove | VLC path field |
| `Config.java` | Comment | Remove VoileMap javadoc |

### No Changes Required

| Component | Reason |
|-----------|--------|
| `OJMParser.java` | Keep as-is (audio extractor) |
| `OJNParser.java` | Keep as-is (O2Jam parser) |
| `OJNChart.java` | Keep as-is (already O2Jam) |
| `Config.java` | Already uses Jackson JSON |
| `partytime.jar` | O2Jam multiplayer feature |

---

## Implementation Timeline

| Phase | Duration | Tasks |
|-------|----------|-------|
| **Phase 1: Parser Cleanup** | Day 1-2 | Remove 11 parser files, update Chart.java |
| **Phase 2: Main Source** | Day 3-4 | Remove DJMax files, update Render.java, MusicSelection.java |
| **Phase 3: GUI Cleanup** | Day 5 | Remove VLC panels, update Config.java comment |
| **Phase 4: Dependencies** | Day 6 | Update build.gradle, remove lib/*.jar |
| **Phase 5: Testing** | Day 7 | Compile test, runtime test, docs update |

---

## Testing Checklist

### Compile Testing
- [ ] `./gradlew clean build` succeeds
- [ ] No missing dependency errors
- [ ] No compilation warnings

### Runtime Testing
- [ ] Application launches without errors
- [ ] OJN charts load correctly
- [ ] Cover images display
- [ ] Keysounds play correctly
- [ ] Gameplay works (notes, judgment, scoring)
- [ ] Keysound extraction works (right-click menu)
- [ ] Configuration saves/loads (JSON)
- [ ] Multiplayer works (Partytime)

### Regression Testing
- [ ] All O2Jam game modes (4K-8K)
- [ ] Skin loading
- [ ] Speed modifiers (HiSpeed, etc.)
- [ ] Volume controls (Master, Key, BGM)

---

## Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Parser removal | Low | No dependencies from main code |
| DJMax removal | Low | Unused in codebase |
| KrazyRain removal | Low | Only used by XNT parser |
| VLCJ removal | Low | Config GUI changes only |
| CharsetDetector removal | Low | Only used by BMS/SM |
| Voile removal | Low | Already not used (JSON) |

**Overall Risk:** LOW - All removals are isolated

---

## Post-Removal Architecture

### Dependencies (4 total)
```
open2jam-modern
├── LWJGL 3        # Rendering, input, audio
├── Partytime      # LAN multiplayer
├── Jackson        # JSON configuration
└── SQLite         # Chart cache
```

### Project Structure
```
open2jam-modern/
├── src/org/open2jam/
│   ├── render/        # LWJGL 3 rendering
│   ├── gui/           # Swing GUI
│   ├── game/          # Game logic
│   ├── parsers/       # OJN/OJM only
│   └── util/          # Utilities
├── lib/
│   └── partytime.jar  # Only external lib
└── build.gradle       # Simplified dependencies
```

---

## Benefits

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Parser files | 22 | 11 | -50% |
| Dependencies | 9 | 4 | -56% |
| lib/ JARs | 9 | 1 | -89% |
| Code formats | 5 | 1 (OJN) | -80% |
| Security surface | High | Low | Reduced |
| Build time | ~30s | ~15s | -50% |

---

## Next Steps

1. **Review this plan** - Ensure all corrections are accurate
2. **Create backup branch** - `git checkout -b backup-before-cleanup`
3. **Begin Phase 1** - Remove parser files
4. **Test incrementally** - After each phase
5. **Update documentation** - README.md, QWEN.md

---

**Approval Required:** Ready to proceed with implementation?
