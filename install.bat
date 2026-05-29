@echo off
set INSTALL_DIR=%USERPROFILE%\AppData\Local\simp-plus
mkdir "%INSTALL_DIR%" 2>nul
xcopy /E /I /Y . "%INSTALL_DIR%"
setx PATH "%PATH%;%INSTALL_DIR%\bin"
echo Installed! Restart your terminal for changes to take effect.