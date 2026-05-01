@echo off
REM Build Columba Desktop Application (Windows)

echo Building Columba Desktop...
call gradlew.bat :desktop:createDistributable

echo Build complete!
echo Output: desktop\build\compose\binaries\
pause
