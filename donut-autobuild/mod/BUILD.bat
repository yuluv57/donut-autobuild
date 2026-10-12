@echo off
rem Build Donut Auto Build bang Gradle (can Java 21 + Internet lan dau, va 3 jar trong libs\ - xem libs\README.md)
setlocal
cd /d "%~dp0"
if not exist libs\litematica-fabric-1.21.4-0.21.7.jar (echo Thieu libs\litematica-fabric-1.21.4-0.21.7.jar - xem libs\README.md & pause & exit /b 1)
call gradlew.bat --no-daemon build
if errorlevel 1 (
  echo Build loi. Can Java 21 va Internet cho lan build dau tien.
  pause
  exit /b 1
)
for /f "tokens=2 delims==" %%v in ('findstr /b "mod_version=" gradle.properties') do set VER=%%v
echo JAR: build\libs\donut-autobuild-fabric-1.21.4-%VER%.jar
pause
