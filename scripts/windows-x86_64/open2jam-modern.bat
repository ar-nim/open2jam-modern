@echo off
REM
REM open2jam-modern - Launch script for Windows
REM
REM This script launches open2jam-modern with appropriate JVM settings.
REM

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "APP_NAME=open2jam-modern"

REM Find the JAR file
for %%f in ("%SCRIPT_DIR%*-all.jar") do (
    set "JAR_FILE=%%f"
    goto :found
)

:found
if not defined JAR_FILE (
    echo Error: Could not find application JAR file in %SCRIPT_DIR%
    echo Expected file: *-all.jar
    pause
    exit /b 1
)

REM Check for Java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Java is not installed or not in PATH
    echo Please install Java 21 or higher.
    pause
    exit /b 1
)

REM Get Java version
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION=%%g"
)

REM Set JVM options
set "JVM_OPTS=-Xmx2G -XX:+UseG1GC -Djava.awt.headless=false -Dfile.encoding=UTF-8"

REM Launch the application
echo Starting %APP_NAME%...
java %JVM_OPTS% -jar "%JAR_FILE%" %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo Application exited with error code %ERRORLEVEL%
    pause
)
