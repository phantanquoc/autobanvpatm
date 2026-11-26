@echo off
:: BatchGotAdmin (Run as Admin code starts)
REM --> Check for permissions
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
REM --> If error flag set, we do not have admin.
if '%errorlevel%' NEQ '0' (
echo Requesting administrative privileges...
goto UACPrompt
) else ( goto gotAdmin )
:UACPrompt
echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
echo UAC.ShellExecute "%~s0", "", "", "runas", 1 >> "%temp%\getadmin.vbs"
"%temp%\getadmin.vbs"
exit /B
:gotAdmin
if exist "%temp%\getadmin.vbs" ( del "%temp%\getadmin.vbs" )
pushd "%CD%"
CD /D "%~dp0"
:: BatchGotAdmin (Run as Admin code ends)
:: Your codes should start from the following line

ECHO NDTPRO
ECHO NINJA_BAN_AUTO_BAN_VAT_PHAM

:: Uu tien Java kem theo goi (BanVatPham\jre), khong co thi dung java trong PATH
set "JAVA_EXE=java"
if exist "%~dp0jre\bin\java.exe" set "JAVA_EXE=%~dp0jre\bin\java.exe"
"%JAVA_EXE%" -jar ninja_ban_new.jar
