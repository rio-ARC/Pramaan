@echo off
:: Pramaan Server Launch Script
echo ====================================================================
echo  Starting Pramaan Verification Authority Server (Port 8080)...
echo ====================================================================
echo.

call gradlew.bat :server:run --no-daemon

pause
