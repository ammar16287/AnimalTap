@echo off
rem Builds Animal Tap APKs locally and copies them to the "apk" folder.
cd /d "%~dp0"

if not defined JAVA_HOME (
  for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio1\jbr" "%ProgramFiles%\Android\Android Studio\jre") do (
    if exist "%%~P\bin\java.exe" if not defined JAVA_HOME set "JAVA_HOME=%%~P"
  )
)

echo Started %DATE% %TIME% > build-log.txt
echo JAVA_HOME=%JAVA_HOME% >> build-log.txt
call gradlew.bat assembleDebug assembleRelease --console=plain >> build-log.txt 2>&1
if errorlevel 1 (
  echo BUILD FAILED >> build-log.txt
  echo Build failed - see build-log.txt
  goto :end
)

if not exist apk mkdir apk
copy /y "app\build\outputs\apk\release\app-release.apk" "apk\AnimalTap.apk" >nul 2>&1
copy /y "app\build\outputs\apk\debug\app-debug.apk" "apk\AnimalTap-debug.apk" >nul 2>&1
echo BUILD OK >> build-log.txt
echo Done! APKs are in the "apk" folder.
:end
echo Finished %DATE% %TIME% >> build-log.txt
timeout /t 5 >nul
