@echo off
rem Windows: uruchom z folderu z adb.exe (platform-tools) albo z adb w PATH.
rem Uzycie: lekki.cmd raport | zastosuj | kompiluj | przywroc | turbo-on | turbo-off
setlocal
if "%~1"=="" (
  echo Uzycie: lekki.cmd raport^|zastosuj^|kompiluj^|przywroc^|turbo-on^|turbo-off
  exit /b 1
)
adb push "%~dp0lekki.sh" "%~dp0pakiety.txt" /data/local/tmp/ || exit /b 1
adb shell sh /data/local/tmp/lekki.sh %1 %2
if /i "%~1"=="raport" adb pull /sdcard/lekki "%~dp0raport-tbox"
endlocal
