@echo off
REM
REM Build script for open2jam-modern (Windows)
REM
REM This script is designed for CI/CD integration (GitHub Actions, etc.)
REM It builds the project and creates distribution packages.
REM
REM Usage:
REM   build.bat [command]
REM
REM Commands:
REM   build           - Build the project (default)
REM   dist            - Create distribution for current platform
REM   dist-all        - Create distributions for all platforms (cross-compile)
REM   dist-platform   - Create distribution for specific platform
REM   clean           - Clean build artifacts
REM   test        - Run tests
REM   all         - Clean, build, test, and create distribution
REM   help        - Show this help message
REM

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM Colors (Windows 10+)
for /F "tokens=1,2 delims=#" %%a in ('"prompt #$H#$E# & echo on & for %%b in (1) do rem"') do (
  set "DEL=%%a"
  set "COLOR_BLUE=%%b[34m"
  set "COLOR_GREEN=%%b[32m"
  set "COLOR_YELLOW=%%b[33m"
  set "COLOR_RED=%%b[31m"
  set "COLOR_RESET=%%b[0m"
)

REM Functions
:log_info
echo %COLOR_BLUE%[INFO]%COLOR_RESET% %~1
goto :eof

:log_success
echo %COLOR_GREEN%[SUCCESS]%COLOR_RESET% %~1
goto :eof

:log_warning
echo %COLOR_YELLOW%[WARNING]%COLOR_RESET% %~1
goto :eof

:log_error
echo %COLOR_RED%[ERROR]%COLOR_RESET% %~1
goto :eof

:show_help
echo Build script for open2jam-modern
echo.
echo Usage: build.bat [command] [platform]
echo.
echo Commands:
echo   build           - Build the project ^(default^)
echo   dist            - Create distribution for current platform
echo   dist-all        - Create distributions for all platforms ^(cross-compile^)
echo   dist-platform   - Create distribution for specific platform
echo   clean           - Clean build artifacts
echo   test            - Run tests
echo   all             - Clean, build, test, and create distribution
echo   help            - Show this help message
echo.
echo Platforms ^(for dist-platform^):
echo   windows-x86_64  - Windows 64-bit ^(Intel/AMD^)
echo   windows-arm64   - Windows ARM64 ^(Snapdragon, Surface Pro X^)
echo   linux-x86_64    - Linux 64-bit ^(Intel/AMD^)
echo   linux-arm64     - Linux ARM64 ^(Raspberry Pi, AWS Graviton^)
echo   macos-x86_64    - macOS Intel ^(64-bit^)
echo   macos-arm64     - macOS Apple Silicon ^(M1/M2/M3^)
echo.
echo Examples:
echo   build.bat                          REM Build the project
echo   build.bat dist                     REM Create distribution for current platform
echo   build.bat dist-all                 REM Create all platform distributions
echo   build.bat dist-platform macos-arm64 REM Create macOS Apple Silicon dist
echo   build.bat all                      REM Full build pipeline
goto :eof

:check_java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    call :log_error "Java is not installed or not in PATH"
    exit /b 1
)

for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION=%%g"
)
call :log_info "Java version: %JAVA_VERSION%"
goto :eof

:check_gradle
if not exist "gradlew.bat" (
    call :log_error "Gradle wrapper not found. Make sure you're in the project root."
    exit /b 1
)
goto :eof

:cmd_build
call :log_info "Building project..."
call gradlew.bat clean build fatJar
if %ERRORLEVEL% neq 0 exit /b 1
call :log_success "Build completed!"
call :log_info "JAR file: build\libs\open2jam-modern-*-all.jar"
goto :eof

:cmd_dist
call :log_info "Creating distribution for current platform..."
call gradlew.bat distZipCurrent
if %ERRORLEVEL% neq 0 exit /b 1

for %%f in (build\libs\open2jam-modern-*-x86_64.zip) do (
    set "ZIP_FILE=%%f"
    goto :found
)

:found
if defined ZIP_FILE (
    call :log_success "Distribution created: %ZIP_FILE%"
) else (
    call :log_error "Distribution ZIP not found!"
    exit /b 1
)
goto :eof

:cmd_dist_all
call :log_info "Creating distributions for all platforms (cross-compilation)..."
call :log_info "Note: The fat JAR is platform-independent (pure Java bytecode)"
call :log_info "Launch scripts are shell/batch scripts that work on all platforms"
call gradlew.bat distZipAll
if %ERRORLEVEL% neq 0 exit /b 1
call :log_success "Distributions created in build\libs\"
dir /b build\libs\*-x86_64.zip build\libs\*-arm64.zip 2>nul
goto :eof

:cmd_dist_platform
set "PLATFORM=%~2"
if "%PLATFORM%"=="" (
    call :log_error "Platform not specified!"
    echo Available platforms: windows-x86_64, windows-arm64, linux-x86_64, linux-arm64, macos-x86_64, macos-arm64
    exit /b 1
)

call :log_info "Creating distribution for platform: %PLATFORM%"

REM Create platform directory
set "platformDir=build\dist\%PLATFORM%"
set "libsDir=build\libs"
if not exist "%platformDir%" mkdir "%platformDir%"

REM Copy fatJar
copy /Y "%libsDir%\*-all.jar" "%platformDir%\" >nul

REM Copy launch scripts
xcopy /Y /I "scripts\%PLATFORM%\*" "%platformDir%\" >nul 2>&1

REM Copy README and LICENSE
copy /Y README.md LICENSE "%platformDir%\" >nul 2>&1

REM Create ZIP using PowerShell (more reliable than built-in zip)
powershell -Command "Compress-Archive -Path '%platformDir%\*' -DestinationPath '%libsDir%\open2jam-modern-%VERSION%-%PLATFORM%.zip' -Force"

call :log_success "Distribution created: %libsDir%\open2jam-modern-%VERSION%-%PLATFORM%.zip"
goto :eof

:cmd_clean
call :log_info "Cleaning build artifacts..."
call gradlew.bat clean
if %ERRORLEVEL% neq 0 exit /b 1
call :log_success "Clean completed!"
goto :eof

:cmd_test
call :log_info "Running tests..."
call gradlew.bat test
if %ERRORLEVEL% neq 0 exit /b 1
call :log_success "Tests completed!"
goto :eof

:cmd_all
call :log_info "Running full build pipeline..."
call :cmd_clean
call :cmd_build
call :cmd_test
call :cmd_dist
call :log_success "Full build pipeline completed!"
goto :eof

REM Main
set "COMMAND=%~1"
if "%COMMAND%"=="" set "COMMAND=build"

if "%COMMAND%"=="build" (
    call :check_java
    call :check_gradle
    call :cmd_build
) else if "%COMMAND%"=="dist" (
    call :check_java
    call :check_gradle
    call :cmd_dist
) else if "%COMMAND%"=="dist-all" (
    call :check_java
    call :check_gradle
    call :cmd_dist_all
) else if "%COMMAND%"=="dist-platform" (
    call :check_java
    call :check_gradle
    call :cmd_dist_platform %COMMAND% %~2
) else if "%COMMAND%"=="clean" (
    call :check_gradle
    call :cmd_clean
) else if "%COMMAND%"=="test" (
    call :check_java
    call :check_gradle
    call :cmd_test
) else if "%COMMAND%"=="all" (
    call :check_java
    call :check_gradle
    call :cmd_all
) else if "%COMMAND%"=="help" (
    call :show_help
) else (
    call :log_error "Unknown command: %COMMAND%"
    call :show_help
    exit /b 1
)
