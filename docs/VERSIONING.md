# Versioning Guide

## Overview

open2jam-modern uses **Semantic Versioning (SemVer)** with dynamic version detection from git tags.

## Version Priority

The build system determines the version in this order:

1. **Command line override** (`-PoverrideVersion=x`) - Manual override
2. **Git tag** (e.g., `0.1.0`) - Release build (CI/CD)
3. **gradle.properties** - Development version (e.g., `0.1.0-SNAPSHOT`)
4. **Fallback** - `0.1.0-SNAPSHOT`

## Development Workflow

### Daily Development

Just build normally - version is from `gradle.properties`:

```bash
./gradlew build
# Version: 0.1.0-SNAPSHOT
```

### Override Version Temporarily

```bash
./gradlew build -PoverrideVersion=0.2.0-custom
```

## Release Workflow

### Creating a Release

1. **Tag the commit** with the version number:
   ```bash
   git tag 0.1.0
   git push origin 0.1.0
   ```

2. **Build the release**:
   ```bash
   ./gradlew clean build fatJar distZipAll
   # Version automatically detected from tag: 0.1.0
   ```

### CI/CD Integration

For GitHub Actions, GitLab CI, or other CI/CD systems:

```yaml
# Example GitHub Actions
on:
  push:
    tags:
      - '*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Important: fetch all tags
      
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build release
        run: ./gradlew clean build fatJar distZipAll
        # Version automatically detected from git tag
```

## Version Format

### Release Versions
```
0.1.0     # First pre-release
0.2.0     # Second pre-release with new features
1.0.0     # First stable release
1.0.1     # Bug fix
1.1.0     # New feature
2.0.0     # Breaking changes
```

### Development Versions
```
0.1.0-SNAPSHOT      # Current development (towards 0.1.0)
0.2.0-SNAPSHOT      # Development towards 0.2.0
1.0.0-SNAPSHOT      # Development towards 1.0.0 (stable)
```

## Configuration Files

### gradle.properties
```properties
# Default development version (used when no git tag exists)
version=0.1.0-SNAPSHOT
```

### build.gradle
The version detection logic is in `build.gradle`:
- `getVersionFromGit()` - Detects version from git tags/commits
- Priority system handles release vs development builds

## When to Increment Version

### Major (X.0.0)
- Breaking changes to configuration files
- Removed features
- Major gameplay changes

### Minor (x.Y.0)
- New features (new speed modifiers, etc.)
- New chart format support
- Significant improvements

### Patch (x.y.Z)
- Bug fixes
- Performance improvements
- Minor tweaks

## Examples

```bash
# Check current version
./gradlew properties --quiet | grep "^version:"

# Build with specific version
./gradlew build -PoverrideVersion=0.3.0-beta

# Create release tag
git tag 0.1.0
git push origin 0.1.0

# Build release artifacts
./gradlew clean build fatJar distZipAll
```

## Troubleshooting

### Version not detected from tag

Ensure you fetched tags:
```bash
git fetch --tags
./gradlew build
```

### Force specific version
```bash
./gradlew build -PoverrideVersion=0.1.0-custom
```

### Check what version will be used
```bash
./gradlew properties --quiet | grep "^version:"
```

### Why is my version still SNAPSHOT?

You're on a development branch without a git tag. This is normal.
To create a release:
```bash
git tag 0.1.0
git push origin 0.1.0
./gradlew build
# Now version will be: 0.1.0
```
