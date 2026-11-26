@echo off
setlocal
cd /d "%~dp0"
set "SRC=%~dp0NinjaLite\GomItem"
set "DST=C:\NinjaLite\GomItem"
echo ===== CAI DAT DU LIEU GAME =====
echo Nguon : %SRC%
echo Dich  : %DST%
echo Chi chep file CHUA CO o dich, khong ghi de file da co.
echo.

if exist "%SRC%\env.json" goto copy_data
echo LOI: khong tim thay %SRC%\env.json
echo Hay giai nen day du goi cai dat roi chay lai cai_dat.bat
pause
exit /b 1

:copy_data

robocopy "%SRC%" "%DST%" /E /XC /XN /XO /NP /NDL /R:1 /W:1
set "RC=%ERRORLEVEL%"
echo.
if %RC% GEQ 8 (
  echo LOI: robocopy that bai, ma loi %RC%. Du lieu game CHUA duoc cai day du.
  pause
  exit /b 1
)
echo Cai dat thanh cong ^(ma robocopy %RC%^). Du lieu game nam tai %DST%
echo Buoc tiep theo: dien BanVatPham\cauhinh.txt ^(ngan hang, SePay, mat khau admin^) va BanVatPham\banvatpham.txt ^(danh sach bot^), xem HUONG_DAN.txt, roi chay BanVatPham\NinjaBan.bat
pause
exit /b 0
