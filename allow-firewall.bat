@echo off
echo Requesting administrator privileges to allow Pramaan port 8080...
netsh advfirewall firewall add rule name="Pramaan Verification Server" dir=in action=allow protocol=TCP localport=8080
echo Inbound TCP port 8080 allowed successfully!
pause
