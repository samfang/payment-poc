@REM Apache Maven Wrapper script for Windows
@REM Licensed to the Apache Software Foundation (ASF)

@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties
set MAVEN_USER_HOME=%USERPROFILE%\.m2

for /F "usebackq tokens=1,* delims==" %%A in ("%MAVEN_WRAPPER_PROPERTIES%") do (
  if "%%A"=="distributionUrl" set DISTRIBUTION_URL=%%B
)

for %%F in ("%DISTRIBUTION_URL%") do set DISTRIBUTION_ID=%%~nF
set DISTRIBUTION_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DISTRIBUTION_ID%
set MAVEN_HOME=%DISTRIBUTION_DIR%\%DISTRIBUTION_ID%

if not exist "%MAVEN_HOME%" (
  echo Downloading Apache Maven from %DISTRIBUTION_URL%
  mkdir "%DISTRIBUTION_DIR%"
  powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%DISTRIBUTION_DIR%\%DISTRIBUTION_ID%.zip'"
  powershell -Command "Expand-Archive -Path '%DISTRIBUTION_DIR%\%DISTRIBUTION_ID%.zip' -DestinationPath '%DISTRIBUTION_DIR%'"
  del "%DISTRIBUTION_DIR%\%DISTRIBUTION_ID%.zip"
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
