@echo off
REM Dong tool cu roi swap jar
taskkill /F /IM java.exe 2>nul
timeout /t 2 >nul
copy /Y ninja_ban_new.jar ninja_ban.jar
echo Swap done.
pause
start javaw -jar ninja_ban.jar

