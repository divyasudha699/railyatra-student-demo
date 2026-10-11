@echo off
setlocal
cd /d "%~dp0"
title RailYatra - Java Reservation Website
where java >nul 2>nul
if errorlevel 1 (
 echo Java 17 JDK not found. Please install Temurin JDK 17.
 pause
 exit /b 1
)
set "MAVEN="
where mvn.cmd >nul 2>nul
if not errorlevel 1 set "MAVEN=mvn.cmd"
if not defined MAVEN if exist "%USERPROFILE%\Tools\apache-maven-3.9.16\bin\mvn.cmd" set "MAVEN=%USERPROFILE%\Tools\apache-maven-3.9.16\bin\mvn.cmd"
if not defined MAVEN if exist "%USERPROFILE%\Tools\apache-maven-3.10.0\bin\mvn.cmd" set "MAVEN=%USERPROFILE%\Tools\apache-maven-3.10.0\bin\mvn.cmd"
if not defined MAVEN (
 echo Maven not found. Install Maven or use the Maven command from PowerShell.
 pause
 exit /b 1
)
echo.
echo ==================== RAILYATRA ====================
echo Starting local website... Please wait.
echo A browser window may open automatically.
echo If it does not, open http://localhost:8080
 echo Keep this window open while using RailYatra.
echo ===================================================
echo.
"%MAVEN%" compile exec:java
pause
