@echo off
chcp 65001 >nul
echo =============================================
echo   五菱汽车 Android App 构建脚本
echo =============================================
echo.

cd /d "%~dp0"

REM 检查 Java 版本
echo [1/4] 检查 Java 环境...
java -version 2>&1 | findstr "version" >nul
if errorlevel 1 (
    echo   Java 未安装!
    echo   请安装 JDK 17+: https://adoptium.net/
    goto :error
)

REM 获取 Java 版本
for /f tokens^=2^=delims^=%%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%i
echo   Java 版本: %JAVA_VER%

REM 检查是否需要 Java 17+
echo %JAVA_VER% | findstr "1.8 1.7 1.6" >nul
if not errorlevel 1 (
    echo.
    echo   [错误] Android Gradle Plugin 8.x 需要 Java 17+
    echo   当前 Java 版本: %JAVA_VER%
    echo   请升级 Java: https://adoptium.net/
    goto :error
)

REM 设置环境变量
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set JAVA_HOME=%ProgramFiles%\Java\jdk-17

REM 检查 Android SDK
echo.
echo [2/4] 检查 Android SDK...
if not exist "%ANDROID_HOME%\platforms" (
    echo   [警告] Android SDK 未找到
    echo   请安装 Android Studio: https://developer.android.com/studio
    goto :error
)
echo   Android SDK: %ANDROID_HOME%

REM 创建 keystore 目录
if not exist "keystore" mkdir keystore

REM 检查 keystore 是否存在
if not exist "keystore\wuling.keystore" (
    echo.
    echo [3/4] 创建签名密钥库...
    echo   密钥库密码: wuling123
    echo   密钥别名: wuling
    echo   密钥密码: wuling123
    "%JAVA_HOME%\bin\keytool" -genkeypair -v -storetype PKCS12 ^
        -keystore "keystore\wuling.keystore" ^
        -alias wuling ^
        -keyalg RSA ^
        -keysize 2048 ^
        -validity 10000 ^
        -storepass wuling123 ^
        -keypass wuling123 ^
        -dname "CN=Wuling, OU=Wuling, O=Wuling, L=Shanghai, ST=Shanghai, C=CN"
    if errorlevel 1 (
        echo.
        echo   [错误] 创建密钥库失败
        goto :error
    )
) else (
    echo.
    echo [3/4] 签名密钥库已存在
)

REM 执行构建
echo.
echo [4/4] 构建 APK...
echo.

call gradlew.bat assembleRelease --no-daemon

if errorlevel 1 (
    echo.
    echo   [错误] 构建失败
    goto :error
)

echo.
echo =============================================
echo   构建成功!
echo =============================================
echo.
echo 输出文件: app\build\outputs\apk\release\app-release.apk
echo.
echo 如需重新签名，使用:
echo   jarsigner -verbose -sigalg SHA1withRSA ^
     -digestalg SHA1 ^
     -keystore keystore\wuling.keystore ^
     app\build\outputs\apk\release\app-release.apk wuling
echo.
pause
exit /b 0

:error
echo.
echo =============================================
echo   构建被中断
echo =============================================
echo.
pause
exit /b 1
