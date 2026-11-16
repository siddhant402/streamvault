@echo off
rem Starts the StreamVault server and web app in two windows for a local demo.
start "StreamVault server" /d "%~dp0server" cmd /k mvnw.cmd spring-boot:run
start "StreamVault web app" /d "%~dp0client" cmd /k npm run dev
echo Started. The server takes about 30 seconds; the web app runs at http://localhost:3000
timeout /t 5 >nul
