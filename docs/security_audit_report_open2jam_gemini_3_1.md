# Security Audit Report: open2jam-modern

## 1. Executive Summary
A comprehensive security review and static analysis was performed on the `open2jam-modern` project. The analysis focused on identifying common vulnerabilities such as Remote Code Execution (RCE), SQL Injection, XML External Entity (XXE) attacks, Path Traversal, and Denial of Service (DoS). 

The application generally exhibits a small attack surface, as it does not expose network listening sockets or process XML/Serialized Java objects. However, because it parses custom binary files (OJN and OJM chart/audio formats) which could be user-supplied or downloaded from third parties, vulnerabilities in these parsers pose a risk to users.

**Key Findings:**
1. **High Risk:** Path Traversal in OJN file parsing ([OJNParser.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNParser.java)).
2. **Medium Risk:** Denial of Service (OOM) via unvalidated length fields in OJM file parsing ([OJMParser.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java)).
3. **Positive Observance:** [ChartCacheSQLite.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/ChartCacheSQLite.java) is well-written with proper use of Parameterized Queries (preventing SQL Injection) and implements robust transaction batching and lazy validation.

---

## 2. Threat Model
**Assets:** User's filesystem, application stability, memory resources.
**Threat Actors:** Malicious chart creators distributing crafted `.ojn` or `.ojm` files.
**Attack Vectors:**
- User downloading and playing a malicious custom song.
- The game's caching mechanism automatically scanning a directory containing a malicious file.

---

## 3. Detailed Findings

### 3.1. Path Traversal in OJM Sample Loading (Arbitrary File Read)
**Severity:** High
**Location:** [parsers/src/org/open2jam/parsers/OJNParser.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNParser.java) (Line ~114-116)
```java
byte ojm_file[] = new byte[32];
buffer.get(ojm_file);
File sample_file = new File(file.getParent(), ByteHelper.toString(ojm_file));
```
**Description:**
The OJN header contains a 32-byte field specifying the associated OJM (audio sample) filename. `ByteHelper.toString(ojm_file)` converts these bytes directly into a string without sanitization. An attacker can craft an OJN file where this field contains path traversal sequences (e.g., `../../../../etc/passwd` or `..\..\..\Windows\win.ini`). 
While the game expects an audio file, pointing it to sensitive files may cause the [OJMParser](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#17-328) to attempt to parse them, potentially crashing the application or exposing file metadata depending on how the error is handled later in the application flow.

**Remediation:**
Sanitize the extracted string to ensure it only represents a simple filename without directory separators.
```java
String ojm_filename = ByteHelper.toString(ojm_file);
// Remove any directory traversal attempts
ojm_filename = new File(ojm_filename).getName(); 
File sample_file = new File(file.getParent(), ojm_filename);
```

### 3.2. Denial of Service via Unvalidated Lengths (OOM / Integer Overflow)
**Severity:** Medium
**Location:** [parsers/src/org/open2jam/parsers/OJMParser.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java) (Line ~138 & ~262)
```java
// parseM30
int sample_size = buffer.getInt();
...
byte[] sample_data = new byte[sample_size]; // Vulnerable allocation
buffer.get(sample_data);

// parseOMC
int sample_size = buffer.getInt();
buffer = f.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY, file_offset, sample_size);
```
**Description:**
The OJM parser reads a `sample_size` integer directly from the binary file and immediately uses it to allocate a byte array (`new byte[sample_size]`) or map memory. An attacker can set `sample_size` to a massive positive integer (e.g., `Integer.MAX_VALUE - 8`), causing an immediate `OutOfMemoryError` which crashes the application. If a negative value is provided, it will throw a `NegativeArraySizeException` or `IllegalArgumentException`, also crashing the thread or application.

**Remediation:**
Validate the `sample_size` against reasonable bounds and the remaining file size before allocation.
```java
int sample_size = buffer.getInt();
if (sample_size < 0 || sample_size > MAX_ALLOWED_SAMPLE_SIZE || sample_size > buffer.remaining()) {
    throw new IOException("Invalid sample size: " + sample_size);
}
```

---

## 4. Hardening Opportunities & Best Practices

1. **Fuzz Testing:** Since the application heavily relies on parsing custom binary formats (OJN/OJM), it is highly recommended to implement a fuzzing harness (e.g., using Jazzer) to automatically discover edge cases, buffer out-of-bounds reads, and unhandled exceptions in [OJNParser](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJNParser.java#13-265) and [OJMParser](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/OJMParser.java#17-328).
2. **Defensive Parsing:** Ensure all `buffer.getInt()`, `buffer.getShort()`, etc., are wrapped in checks to ensure `buffer.remaining()` is sufficient, preventing unexpected `BufferUnderflowException` crashes when parsing truncated files.
3. **ZLIB Decompression Safeguards:** In [Compressor.java](file:///home/arnim/projects/o2jam/open2jam-modern/parsers/src/org/open2jam/parsers/utils/Compressor.java), while the standard `Inflater` is used, consider enforcing a maximum output size limit to mitigate potential "zip bomb" (decompression ratio) attacks if compressed buffers are read from untrusted files.
4. **Dependency Management:** Ensure external dependencies (if any are added) are scanned for known CVEs using tools like OWASP Dependency-Check.

---
**Audit Conclusion:**
The codebase is generally secure against severe compromise (e.g., RCE), primarily because it is a local client application without server components. Addressing the path traversal and memory allocation issues in the parsers will significantly improve the client's resilience against malicious files.
