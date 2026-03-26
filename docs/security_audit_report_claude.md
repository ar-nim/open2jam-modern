# Security Audit Report: open2jam-modern

**Scope:** Full end-to-end code trace of all 90 Java source files, build configuration, and dependencies.
**Date:** 2026-03-26
**Auditor:** Antigravity Static Analysis

---

## Executive Summary

The open2jam-modern project is a desktop rhythm game client that parses custom binary file formats (OJN/OJM), renders gameplay via OpenGL/LWJGL, persists settings in JSON and SQLite, and supports optional local multiplayer over TCP. The primary attack surface is **malicious chart files** (OJN/OJM) distributed by untrusted third parties.

| Severity | Count | Key Areas |
|----------|-------|-----------|
| 🔴 Critical | 1 | XXE injection in XML parsing |
| 🟠 High | 3 | Path traversal, unbounded decompression, unvalidated offsets |
| 🟡 Medium | 4 | OOM via allocation, thread-unsafe state, insecure network, file write traversal |
| 🔵 Low | 4 | Shared mutable buffer, resource leaks, Jackson deserialization, debug info leak |

---

## Threat Model

- **Assets:** User's filesystem, application stability, memory, local network
- **Threat Actors:** Malicious chart creators distributing crafted `.ojn`/`.ojm` files; LAN attackers on the local matching network
- **Attack Vectors:**
  1. User opens a malicious OJN/OJM file
  2. Automatic scanning of a directory containing malicious files
  3. Man-in-the-middle or rogue server on the local matching network
  4. Crafted `config.json` file

---

## Findings

### 🔴 CRITICAL-01: XXE Injection in SAX XML Parser

> [!CAUTION]
> The default `SAXParserFactory` is used without disabling external entities or DTD processing.

**Location:** [Render.java](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/Render.java#L459)

```java
SAXParserFactory.newInstance().newSAXParser().parse(resources_xml.openStream(), sb);
```

**Impact:** While `resources_xml` is currently loaded from the classpath (low risk), the SAXParser itself is not hardened. If the XML source were ever changed to load from an external file (e.g., user-selectable skin), an attacker could inject an XXE payload to:
- Read arbitrary files from the user's filesystem
- Trigger SSRF (Server-Side Request Forgery) to internal network
- Cause DoS via "Billion Laughs" entity expansion

**Related CVE:** CWE-611, similar to CVE-2018-1000840

**Remediation:**
```java
SAXParserFactory factory = SAXParserFactory.newInstance();
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
factory.newSAXParser().parse(resources_xml.openStream(), sb);
```

---

### 🟠 HIGH-01: Path Traversal in OJN Sample File Reference

**Location:** [OJNParser.java:116](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNParser.java#L114-L116)

```java
byte ojm_file[] = new byte[32];
buffer.get(ojm_file);
File sample_file = new File(file.getParent(), ByteHelper.toString(ojm_file));
```

**Impact:** An attacker crafts an OJN file with a 32-byte field containing `../../../../etc/passwd`. The parser constructs a [File](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#73-107) path that escapes the chart directory. When the OJM parser attempts to read this file, it may crash, leak file metadata, or trigger unexpected behavior.

**Remediation:**
```java
String ojm_filename = ByteHelper.toString(ojm_file);
ojm_filename = new File(ojm_filename).getName(); // Strip path components
File sample_file = new File(file.getParent(), ojm_filename);
```

---

### 🟠 HIGH-02: Unbounded ZLIB Decompression (Zip Bomb)

**Location:** [Compressor.java:22-57](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/Compressor.java#L22-L57)

```java
public static ByteBuffer decompress(ByteBuffer in) {
    // ...
    ByteArrayOutputStream bos = new ByteArrayOutputStream(bin.length);
    byte[] buf = new byte[1024];
    while(true) {
        int count = decompressor.inflate(buf);
        if(count == 0 && decompressor.finished()) break;
        bos.write(buf, 0, count);
    }
    // No size limit!
}
```

**Impact:** A malicious compressed payload with a high compression ratio (zip bomb) can expand to gigabytes, causing `OutOfMemoryError` and crashing the application. A 1KB compressed input could decompress to 1GB+.

**Related CVE:** CWE-409 (Improper Handling of Highly Compressed Data)

**Remediation:**
```java
private static final int MAX_DECOMPRESSED_SIZE = 50 * 1024 * 1024; // 50MB max
long totalOutput = 0;
while(true) {
    int count = decompressor.inflate(buf);
    if(count == 0 && decompressor.finished()) break;
    totalOutput += count;
    if (totalOutput > MAX_DECOMPRESSED_SIZE) {
        throw new IOException("Decompressed data exceeds maximum allowed size");
    }
    bos.write(buf, 0, count);
}
```

---

### 🟠 HIGH-03: Unvalidated File Offsets and Sizes in OJN Chart/Cover Loading

**Location:** [OJNChart.java:106](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNChart.java#L100-L114), [OJNParser.java:172](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNParser.java#L169-L172)

```java
// OJNChart.getCover() - no validation on cover_offset or cover_size
ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, cover_offset, cover_size);

// OJNParser.parseChart() - no validation on note_offset or note_offset_end
ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, start, end - start);
```

**Impact:** Malicious header values for `cover_offset`, `cover_size`, `note_offset`, or `note_offset_end` can:
- Cause `IllegalArgumentException` (negative size or offset) → crash
- Read beyond file boundaries → crash or information leak from mapped memory
- Allocate massive memory-mapped buffers → OOM

**Remediation:** Validate all offsets and sizes against the actual file length before mapping:
```java
long fileLength = f.length();
if (cover_offset < 0 || cover_size <= 0 || cover_offset + cover_size > fileLength) {
    Logger.global.warning("Invalid cover offset/size in OJN file");
    return null;
}
```

---

### 🟡 MEDIUM-01: Denial of Service via Unvalidated Allocation in OJM Parser

**Location:** [OJMParser.java:148](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#L138-L149), [OJMParser.java:262-266](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#L262-L266)

```java
// parseM30 - unchecked allocation
int sample_size = buffer.getInt();
byte[] sample_data = new byte[sample_size]; // Can be Integer.MAX_VALUE

// parseOMC - unchecked mmap
int sample_size = buffer.getInt();
buffer = f.getChannel().map(..., file_offset, sample_size);
```

**Impact:** Setting `sample_size` to `Integer.MAX_VALUE` causes `OutOfMemoryError`. A negative value causes `NegativeArraySizeException` or `IllegalArgumentException`.

**Remediation:**
```java
if (sample_size < 0 || sample_size > buffer.remaining()) {
    Logger.global.warning("Invalid sample size: " + sample_size);
    break;
}
```

---

### 🟡 MEDIUM-02: Thread-Unsafe Static Mutable State in OJMParser

**Location:** [OJMParser.java:304-305](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#L304-L305)

```java
private static int acc_keybyte = 0xFF;
private static int acc_counter = 0;
```

**Impact:** The [OMC_xor](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#306-327) decryption uses shared static mutable state that is reset at line 201-202. If multiple OJM files are parsed concurrently (e.g., during library scanning), the decryption state is corrupted across threads, leading to:
- Garbled audio output
- Potential buffer reads of wrong data
- Unpredictable behavior

**Remediation:** Move `acc_keybyte` and `acc_counter` into instance variables or pass them as method parameters.

---

### 🟡 MEDIUM-03: Insecure Network Client (Plaintext TCP, No Authentication)

**Location:** [Render.java:684-688](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/Render.java#L684-L688)

```java
String[] data = localMatchingServer.trim().split(":");
if (data.length == 2) {
    String host = data[0];
    int port = Integer.parseInt(data[1]);
    localMatching = new Client(host, port, (long)audioLatency.getLatency());
}
```

**Impact:** The local matching client connects to an arbitrary host:port specified by the user without:
- TLS/SSL encryption (data in transit is plaintext)
- Authentication (any server can impersonate)
- Input validation on server responses (via `partytime.jar`)

An attacker on the same network can:
- MitM the connection and inject malicious data
- Set up a rogue server to send crafted payloads

> [!NOTE]
> `partytime.jar` is an opaque third-party binary with no source code available for audit. This is a significant blind spot.

**Remediation:**
1. Add hostname/IP validation (restrict to LAN ranges)
2. Implement TLS or authenticated channels
3. Consider open-sourcing or auditing `partytime.jar`

---

### 🟡 MEDIUM-04: Path Traversal in SampleData.copyToFolder()

**Location:** [SampleData.java:59-71](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/SampleData.java#L59-L71)

```java
public void copyToFolder(File directory) throws IOException {
    File f = new File(directory, filename);  // filename comes from OJM binary data
    if(!f.exists()) {
        FileOutputStream out = new FileOutputStream(f);
        // ...writes data to f
    }
}
```

**Impact:** The `filename` field originates from untrusted OJM binary data (the 32-byte sample name). If it contains [../../../.bashrc](file:///home/arnim/.bashrc) or similar traversal sequences, this method writes arbitrary files to locations outside the target directory.

**Remediation:**
```java
String safeName = new File(filename).getName();
File f = new File(directory, safeName);
```

---

### 🔵 LOW-01: Shared Static Mutable Buffer in ByteHelper

**Location:** [ByteHelper.java:15](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/ByteHelper.java#L15)

```java
public static final byte[] tmp_buffer = new byte[1024];
```

**Impact:** A single shared 1024-byte buffer is used by [copyTo()](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/ByteHelper.java#35-43) across all callers and threads. Concurrent calls will corrupt each other's data, potentially writing corrupted audio samples to disk.

**Remediation:** Allocate buffer locally in [copyTo()](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/ByteHelper.java#35-43):
```java
public static void copyTo(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[1024]; // thread-local allocation
    // ...
}
```

---

### 🔵 LOW-02: Resource Leak — MappedByteBuffer Not Released

**Location:** [OJNParser.java:32](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNParser.java#L32), [OJMParser.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java) (multiple lines)

**Impact:** `FileChannel.map()` returns a `MappedByteBuffer` that is NOT released when the `RandomAccessFile` is closed. On Windows, this prevents file deletion/modification until GC collects the buffer (non-deterministic). On all platforms, it pins native memory.

> [!NOTE]
> The [ChartCacheSQLite](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/ChartCacheSQLite.java#61-1219) class correctly uses `RandomAccessFile` with `readFully()` instead of `MappedByteBuffer` for new code (Fix #6 in its documentation).

**Remediation:** Replace memory-mapped I/O with standard I/O (`RandomAccessFile.readFully()`) in parsers, matching the pattern already used in [ChartCacheSQLite](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/ChartCacheSQLite.java#61-1219).

---

### 🔵 LOW-03: Jackson Deserialization of Config File

**Location:** [Config.java:137](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/Config.java#L137)

```java
Config config = MAPPER.readValue(CONFIG_FILE, Config.class);
```

**Impact:** Jackson 3.x is used to deserialize `config.json` into a [Config](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/Config.java#64-621) object. While Jackson 3.x has default type handling disabled (no polymorphic deserialization by default), a crafted config file could:
- Set extreme values (e.g., `displayWidth: 999999999`) causing resource exhaustion
- Contain unexpected field types triggering uncaught exceptions

**Remediation:** Add validation after deserialization:
```java
if (config.gameOptions.displayWidth < 640 || config.gameOptions.displayWidth > 7680) {
    config.gameOptions.displayWidth = 1280;
}
```

---

### 🔵 LOW-04: Debug Information Leak

**Location:** [Render.java:649-650](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/Render.java#L649-L650)

```java
System.out.println("[DEBUG] Render font: " + trueTypeFont.getFont().getName() + 
                   " (family=" + trueTypeFont.getFont().getFamily() + ")");
```

**Impact:** Debug output leaks system information (font names, family) that could aid fingerprinting. Multiple `System.out.println` and `e.printStackTrace()` calls throughout the codebase leak stack traces.

**Remediation:** Use the Logger framework consistently and set appropriate log levels.

---

## Dependency Analysis

| Dependency | Version | Status |
|-----------|---------|--------|
| LWJGL | 3.4.1 | ✅ Current |
| Jackson | 3.1.0 | ✅ Current |
| SQLite JDBC | 3.51.2.0 | ✅ Current |
| `partytime.jar` | Unknown | ⚠️ **Unauditable** — opaque binary in `lib/` |

> [!IMPORTANT]
> `partytime.jar` is a bundled binary JAR with no source code, no version, and no CVE tracking. It handles network I/O for local multiplayer. This is a significant blind spot.

**Recommendation:** Run OWASP Dependency-Check and consider replacing `partytime.jar` with a source-available alternative or vendoring its source.

---

## Hardening Recommendations

### Priority 1 — Immediate Fixes
1. **Harden SAXParserFactory** — Disable XXE (CRITICAL-01)
2. **Sanitize OJN filename field** — Strip path components (HIGH-01)
3. **Bound decompression output** — Add size limit to `Compressor.decompress()` (HIGH-02)
4. **Validate all file offsets** — Check against file.length() before mapping (HIGH-03)

### Priority 2 — Medium-Term
5. **Validate OJM sample sizes** — Check `buffer.remaining()` before allocation (MEDIUM-01)
6. **Fix OJMParser static state** — Make decryption state instance-local (MEDIUM-02)
7. **Sanitize SampleData filename** — Strip path in [copyToFolder()](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/SampleData.java#59-72) (MEDIUM-04)
8. **Replace MappedByteBuffer** — Use standard I/O in parsers (LOW-02)

### Priority 3 — Best Practices
9. **Fuzz testing** — Implement Jazzer fuzzing harness for OJN/OJM parsers
10. **Remove debug output** — Replace `System.out.println` with Logger
11. **Audit partytime.jar** — Source the code or replace with open-source alternative
12. **Add defensive parsing** — Check `buffer.remaining()` before every `getInt()`/`getShort()` call to avoid `BufferUnderflowException` on truncated files
13. **Validate config values** — Add bounds checking after Jackson deserialization

---

## Positive Observations

| Area | Finding |
|------|---------|
| SQL Injection | ✅ All SQL uses `PreparedStatement` parameterization |
| Transaction Safety | ✅ Bulk operations use proper `beginBulkInsert/commitBulkInsert` with rollback |
| Path Traversal in Library | ✅ `Library.getFullPath()` checks for `..` sequences |
| Cover Size Validation | ✅ `ChartCacheSQLite.getCoverFromCache()` caps cover at 10MB |
| SHA-256 Integrity | ✅ Constant-time comparison, per-call MessageDigest instances |
| Concurrency | ✅ [Config](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/Config.java#64-621) uses `ConcurrentHashMap` and synchronized save |
| Shutdown | ✅ `ChartCacheSQLite.close()` and `hashExecutor` have graceful shutdown |
