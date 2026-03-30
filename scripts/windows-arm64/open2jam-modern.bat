@echo off
REM
REM open2jam-modern - Launch script for Windows ARM64
REM
REM This script launches open2jam-modern with appropriate JVM settings.
REM Optimized for Windows on ARM (Surface Pro X, Snapdragon, etc.)
REM

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "APP_NAME=open2jam-modern"

REM Find the JAR file (look in parent directory)
for %%f in ("%SCRIPT_DIR%..\\*-all.jar") do (
    set "JAR_FILE=%%f"
    goto :found
)

:found
if not defined JAR_FILE (
    echo Error: Could not find application JAR file in %SCRIPT_DIR%..
    echo Expected file: *-all.jar
    pause
    exit /b 1
)

REM Check for Java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Java is not installed or not in PATH
    echo Please install Java 21+ for ARM64.
    echo.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

REM Get Java version
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION=%%g"
)

REM Set JVM options with dynamic heap sizing (30% of system RAM)
REM Capped at 16GB max RAM to prevent excessive heap on high-memory systems
REM Optimized for ARM64 processors with ZGC for low-latency
set "JVM_OPTS=-XX:MaxRAM=16G -XX:MinRAMPercentage=30.0 -XX:MaxRAMPercentage=30.0 -XX:+UseZGC -XX:+AlwaysPreTouch -XX:+ExplicitGCInvokesConcurrent -XX:ZAllocationSpikeTolerance=2 -Djava.awt.headless=false -Dfile.encoding=UTF-8"

REM Launch the application
echo Starting %APP_NAME%...
echo Java: %JAVA_VERSION%
echo Heap size: 30%% of system RAM (dynamic)
java %JVM_OPTS% -jar "%JAR_FILE%" %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo Application exited with error code %ERRORLEVEL%
    pause
)
