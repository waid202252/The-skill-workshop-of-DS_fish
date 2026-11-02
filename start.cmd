@echo off
rem ===========================================================================
rem  WeLiao one-click launcher (ASCII only - do NOT put non-ASCII text here).
rem
rem  Why ASCII only:
rem    cmd.exe parses .cmd files byte-by-byte using the system code page (GBK on
rem    Chinese Windows). If this file contains UTF-8 Chinese AND switches code
rem    page with chcp, the parser loses byte alignment and breaks the script
rem    into fragments, so the window closes instantly. All Chinese output lives
rem    in scripts\start.ps1, which is UTF-8 with BOM and safe there.
rem ===========================================================================

setlocal
cd /d "%~dp0"

set "PS1=%~dp0scripts\start.ps1"

if not exist "%PS1%" (
    echo [ERROR] scripts\start.ps1 not found.
    echo         Make sure you run this from the repository root.
    goto :finish
)

rem Prefer PowerShell 7 (better UTF-8 support), fall back to Windows PowerShell.
set "RUNNER=powershell"
where pwsh >nul 2>nul
if not errorlevel 1 set "RUNNER=pwsh"

"%RUNNER%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %*
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
    echo.
    echo [exit code: %RC%]
)

rem ASCII only. Saved by start.ps1 on every run.
echo.
echo [hint] Last access info saved to: %~dp0runtime\last-url.txt
echo [hint] Query it later with:       start.cmd -Url

:finish
echo.
echo ==========================================
echo  Press any key to close this window...
echo ==========================================
pause >nul
exit /b
