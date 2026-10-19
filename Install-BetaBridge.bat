@echo off
cd /d "%~dp0"
py -3 install.py %*
if errorlevel 1 pause
