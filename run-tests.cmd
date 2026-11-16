@echo off
rem Runs the StreamVault test suites (server, then web app) and saves the output to test-output.txt
set "OUT=%~dp0test-output.txt"
set FORCE_COLOR=0
set NO_COLOR=1

echo Running server tests... this takes a minute or two.
echo ===== SERVER TESTS =====> "%OUT%"
cd /d "%~dp0server"
call mvnw.cmd test >> "%OUT%" 2>&1
echo Server exit code: %ERRORLEVEL%>> "%OUT%"

echo Running web app tests (the first run downloads the test tools)...
echo ===== WEB APP TESTS =====>> "%OUT%"
cd /d "%~dp0client"
call npm install --no-audit --no-fund >> "%OUT%" 2>&1
call npx vitest run --reporter=verbose >> "%OUT%" 2>&1
echo Web app exit code: %ERRORLEVEL%>> "%OUT%"

echo.
echo Done. Results saved to test-output.txt
pause
