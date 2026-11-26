@echo off
setlocal
cd /d "%~dp0"
echo ===== CAP NHAT ninja_ban_new.jar =====
echo.

if not exist "update\ninja_ban_new.jar" (
  echo Khong tim thay file update\ninja_ban_new.jar
  echo Hay dat file ninja_ban_new.jar moi vao thu muc BanVatPham\update\ roi chay lai update.bat
  pause
  exit /b 1
)

if not exist "ninja_ban_new.jar" goto install_new

rem Kiem tra jar co dang bi bot giu khong: doi ten thu, neu bot dang chay thi Windows khoa file va lenh ren that bai.
ren "ninja_ban_new.jar" "ninja_ban_new.jar.locktest" 2>nul
if errorlevel 1 (
  echo Hay tat bot truoc khi update
  pause
  exit /b 1
)
ren "ninja_ban_new.jar.locktest" "ninja_ban_new.jar"
if errorlevel 1 (
  echo LOI: khong doi ten lai duoc ninja_ban_new.jar.locktest thanh ninja_ban_new.jar
  echo Hay tu doi ten file do ve ninja_ban_new.jar roi chay lai update.bat
  pause
  exit /b 1
)

rem Giu 1 ban cu duy nhat de rollback
if exist "ninja_ban_new.jar.old" del /f /q "ninja_ban_new.jar.old"
if exist "ninja_ban_new.jar.old" (
  echo LOI: khong xoa duoc ninja_ban_new.jar.old cu
  pause
  exit /b 1
)

ren "ninja_ban_new.jar" "ninja_ban_new.jar.old"
if errorlevel 1 (
  echo LOI: khong doi ten duoc ninja_ban_new.jar - Hay tat bot truoc khi update
  pause
  exit /b 1
)

move /y "update\ninja_ban_new.jar" "ninja_ban_new.jar" >nul
if errorlevel 1 goto restore
if not exist "ninja_ban_new.jar" goto restore
echo Cap nhat thanh cong. Ban cu duoc luu tai ninja_ban_new.jar.old
goto run

:install_new
move /y "update\ninja_ban_new.jar" "ninja_ban_new.jar" >nul
if errorlevel 1 (
  echo LOI: khong chep duoc update\ninja_ban_new.jar
  pause
  exit /b 1
)
echo Da cai ninja_ban_new.jar moi.
goto run

:restore
echo LOI: khong chep duoc jar moi, dang khoi phuc ban cu...
if exist "ninja_ban_new.jar" del /f /q "ninja_ban_new.jar"
ren "ninja_ban_new.jar.old" "ninja_ban_new.jar"
if errorlevel 1 (
  echo LOI NGHIEM TRONG: hay tu doi ten ninja_ban_new.jar.old thanh ninja_ban_new.jar
) else (
  echo Da khoi phuc ban cu. File moi van con trong thu muc update\
)
pause
exit /b 1

:run
echo.
echo Dang khoi dong bot...
endlocal
call "%~dp0NinjaBan.bat"
