#!/bin/bash
# 五菱汽车 Android App 构建脚本 (macOS/Linux)

set -e

cd "$(dirname "$0")"

echo "============================================="
echo "  五菱汽车 Android App 构建脚本"
echo "============================================="
echo ""

# 检查 Java 版本
echo "[1/4] 检查 Java 环境..."
if ! command -v java &> /dev/null; then
    echo "  [错误] Java 未安装!"
    echo "  请安装 JDK 17+: https://adoptium.net/"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "  Java 版本: $JAVA_VER"

# 检查 Java 17+
if [[ "$JAVA_VER" == "1.8"* ]] || [[ "$JAVA_VER" == "1.7"* ]] || [[ "$JAVA_VER" == "1.6"* ]]; then
    echo ""
    echo "  [错误] Android Gradle Plugin 8.x 需要 Java 17+"
    echo "  当前 Java 版本: $JAVA_VER"
    echo "  请升级 Java: https://adoptium.net/"
    exit 1
fi

# 设置环境变量
export ANDROID_HOME="${ANDROID_HOME:-/usr/local/share/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"

# 检查 Android SDK
echo ""
echo "[2/4] 检查 Android SDK..."
if [ ! -d "$ANDROID_HOME/platforms" ]; then
    echo "  [警告] Android SDK 未找到于: $ANDROID_HOME"
    echo "  请安装 Android Studio 或设置 ANDROID_HOME"
    exit 1
fi
echo "  Android SDK: $ANDROID_HOME"

# 创建 keystore 目录
mkdir -p keystore

# 检查 keystore 是否存在
if [ ! -f "keystore/wuling.keystore" ]; then
    echo ""
    echo "[3/4] 创建签名密钥库..."
    echo "  密钥库密码: wuling123"
    echo "  密钥别名: wuling"
    echo "  密钥密码: wuling123"

    "$JAVA_HOME/bin/keytool" -genkeypair -v -storetype PKCS12 \
        -keystore "keystore/wuling.keystore" \
        -alias wuling \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass wuling123 \
        -keypass wuling123 \
        -dname "CN=Wuling, OU=Wuling, O=Wuling, L=Shanghai, ST=Shanghai, C=CN"
else
    echo ""
    echo "[3/4] 签名密钥库已存在"
fi

# 执行构建
echo ""
echo "[4/4] 构建 APK..."
echo ""

# 清理并构建
./gradlew clean assembleRelease --no-daemon

echo ""
echo "============================================="
echo "  构建成功!"
echo "============================================="
echo ""
echo "输出文件: app/build/outputs/apk/release/app-release.apk"
echo ""

# 显示 APK 信息
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    ls -lh app/build/outputs/apk/release/app-release.apk
fi
