@echo off
:: Pramaan Windows Firewall Helper Script
:: Run this once as Administrator on your laptop to allow physical phones to connect to port 8080.

echo ====================================================================
echo  PRAMAAN: Adding Windows Firewall Rule for Port 8080
echo ====================================================================
echo.

net session >nul 2>&1
if %errorLevel% == 0 (
    echo Administrator permissions confirmed.
) else (
    echo [ERROR] This script requires Administrator privileges!
    echo Please right-click allow-firewall.bat and choose "Run as administrator".
    echo.
    pause
    exit /b 1
)

echo Adding inbound rule for TCP port 8080...
netsh advfirewall firewall add rule name="Pramaan Verification Server (Port 8080)" dir=in action=allow protocol=TCP localport=8080 profile=any

if %errorLevel% == 0 (
    echo.
    echo [SUCCESS] Inbound rule added! Phones on your Wi-Fi/Hotspot can now connect to port 8080.
) else (
    echo.
    echo [WARNING] Command failed. Please check Windows Firewall settings manually.
)

echo.
pause
