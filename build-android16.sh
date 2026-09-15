#!/usr/bin/env bash
#
# 五菱车控 App —— Android 16 (API 36) 一键构建脚本
#
# 由于 Android SDK / Google Maven 在国内网络不可直连，本脚本使用
# 预置 Android SDK 36 的 Docker 镜像，并注入国内镜像源完成依赖下载。
#
# 用法：
#   ./build-android16.sh            # 构建 release APK
#   ./build-android16.sh debug      # 构建 debug APK
#
set -euo pipefail
cd "$(dirname "$0")"

IMAGE="${IMAGE:-mingc/android-build-box:latest}"
CONTAINER="${CONTAINER:-wuling-android16-build}"
VARIANT="${1:-release}"
GRADLE_TASK="assemble${VARIANT^}"

echo "=============================================="
echo "  五菱车控 App · Android 16 (API 36) 构建"
echo "=============================================="

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未找到 docker，请先安装 Docker。"
  exit 1
fi

# 1) 准备构建容器
if ! docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "[1/5] 创建构建容器 $CONTAINER ..."
  docker run -d --name "$CONTAINER" -v "$PWD":/project -w /project "$IMAGE" sleep infinity >/dev/null
else
  echo "[1/5] 复用已有容器 $CONTAINER"
  docker start "$CONTAINER" >/dev/null 2>&1 || true
fi

# 2) 注入国内镜像（Google Maven / Maven Central 不可直连时的替代源）
echo "[2/5] 注入仓库镜像配置..."
docker exec "$CONTAINER" mkdir -p /root/.gradle/init.d
docker cp build-env/mirrors.gradle "$CONTAINER":/root/.gradle/init.d/mirrors.gradle

# 3) 生成签名密钥（release 用）
echo "[3/5] 检查签名密钥..."
mkdir -p keystore
if [ ! -f keystore/wuling.keystore ]; then
  docker exec -w /project "$CONTAINER" bash -c 'keytool -genkeypair -v -storetype PKCS12 \
    -keystore keystore/wuling.keystore -alias wuling -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass wuling123 -keypass wuling123 \
    -dname "CN=Wuling, OU=Wuling, O=Wuling, L=Shanghai, ST=Shanghai, C=CN"' >/dev/null
  echo "  已生成 keystore/wuling.keystore"
else
  echo "  签名密钥已存在"
fi

# 4) 编译
echo "[4/5] 执行 ./gradlew :app:$GRADLE_TASK ..."
docker exec -w /project "$CONTAINER" bash -c "chmod +x gradlew && ./gradlew :app:$GRADLE_TASK --no-daemon"

# 5) 输出产物
echo "[5/5] 构建完成，产物如下："
docker exec -w /project "$CONTAINER" bash -c "find app/build/outputs/apk -name '*.apk' -exec ls -lh {} \;"

echo
echo "提示：安装前请先卸载旧版本（签名不同会安装失败）。"
