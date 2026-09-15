# 五菱车控

> 面向 **Android 16** 的五菱车联网第三方客户端，附带桌面小组件、能耗统计、App 内自动更新等增强功能。

[![最新版本](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fedgeone.gh-proxy.org%2Fhttps%3A%2F%2Fraw.githubusercontent.com%2Fdaiyuxiang520%2Fgeren-update%2Fmain%2Fupdate.json&query=%24.versionName&label=最新版本&color=blue)](https://github.com/daiyuxiang520/geren-update/releases/latest)
[![Android](https://img.shields.io/badge/Android-16%20(targetSdk%2036)-green)]()
[![包名](https://img.shields.io/badge/包名-com.wuling.app.repack-orange)]()

## 简介

**五菱车控**是一款面向 Android 16（API 36）的五菱车联网客户端，基于官方 API 重新实现，独立包名 `com.wuling.app.repack`，**无需官方 App 即可单独使用**。适用于官方 App 在 Android 16 上存在兼容问题，或希望获得更丰富车控与统计功能的场景。

## 功能特性

- **车辆状态**：续航、电量、油量、车门/车窗/后备箱状态实时查看
- **车辆控制**：远程上锁/解锁、车窗、寻车、空调
- **位置服务**：车辆定位与轨迹，逆地理解析中文地址，显示当地天气
- **能耗统计**：日/月/年三维度能耗，趋势图 + 能量构成饼图
- **桌面小组件**：2×3 车辆状态卡片，独立联网刷新
- **自动更新**：启动检测新版本，15 个 GitHub 加速源自动测速择优、失败回退、MD5 校验
- **蓝牙数字钥匙**：BLE 无感连接、靠近自动解锁
- **调试日志**：API 报文（脱敏）落盘持久化，可筛选、搜索、导出

## 下载安装

前往 [**Releases**](https://github.com/daiyuxiang520/geren-update/releases/latest) 下载 `wuling-assistant.apk` 覆盖安装（包名不变，可覆盖历史版本）。GitHub 直链在国内可能无法访问，可任选一个加速镜像替换域名：

```
https://edgeone.gh-proxy.org/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
https://ghproxy.net/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
```

## 自动更新

App 启动时自动检查更新（也可在「我的 → 检查更新」手动触发），更新配置托管于本仓库的 [`update.json`](./update.json)。

- **检查更新**：并发请求 15 个加速源取版本号最大者，避免单源缓存旧版
- **下载**：自动测速择优（上次最快源优先，失败自动回退）→ MD5 校验 → 调起安装器；全部失败自动回退浏览器下载
- 更新弹窗内可**手动选择下载源**（仅本次生效），每个源实时显示延迟

加速源列表提取自 [XIU2/UserScript](https://github.com/XIU2/UserScript)「Github 增强 - 高速下载」，经本地实测筛选。

## 开源致谢

| 项目 | 贡献 |
|---|---|
| [hasscc/wuling](https://github.com/hasscc/wuling)（MIT） | 车控协议与接口思路来源；本项目以原生 Android 独立实现 |
| [XIU2/UserScript](https://github.com/XIU2/UserScript)（GPL-3.0） | 自动更新所用的 GitHub 公益加速源清单 |

其它依赖：Jetpack Compose、Kotlin Coroutines、OkHttp、Gson、Hilt、Coil、DataStore、Security Crypto、友盟+ U-App。

## 构建

仓库不含任何密钥，敏感配置通过 `local.properties` 或环境变量注入。

```bash
# 1. 凭据配置
cp local.properties.example local.properties   # 按注释填写（缺失部分功能不可用，但可正常构建）

# 2. 签名（可选，未设置时 release 回退 debug 签名）
export WULING_KEYSTORE_PATH=/path/to/your.keystore
export WULING_KEYSTORE_PASSWORD=your_store_password
export WULING_KEY_ALIAS=your_key_alias
export WULING_KEY_PASSWORD=your_key_password

# 3. 构建（需 JDK 17 + Android SDK 36）
./gradlew assembleRelease                       # 产物在 app/build/outputs/apk/release/
./build-android16.sh                            # 或使用预置 Docker 镜像构建
```

## 版本历史

| 版本 | 说明 |
|---|---|
| **v60** (3.57.0) | 版本号去掉「-android16」后缀；README 精简 |
| **v59** (3.56.0) | 「调试日志」全量重做：API 报文接入（脱敏）、落盘持久化、筛选/搜索/自动刷新、单条复制、导出分享；蓝牙日志并轨 |
| **v58** (3.55.0) | 详情页移除「车辆信息」区块，首屏直达实时状态（档案移至「我的」页车辆卡） |

<details>
<summary>点击展开更早版本（v41 ~ v59）</summary>

| 版本 | 说明 |
|---|---|
| **v57** (3.54.0) | 首页状态行独占整行不再折行；移除详情页上/下电横幅 |
| **v56** (3.53.0) | 上电/下电状态全 App 打通；「启动」按钮按真实状态反馈 |
| **v55** (3.52.0) | 能耗趋势图点击查看明细；新增能量构成饼图；修复日维度 X 轴标签 |
| **v54** (3.51.0) | 能耗数据页新增「能耗趋势」柱状图 |
| **v53** | 首页状态行新增车窗状态（未关时橙色高亮） |
| **v52** | 位置页地图预览图升级为 3D |
| **v51** (3.50.0) | 全屏地图 3D 视图 + 罗盘控制盘，不支持 WebGL 自动回落 2D |
| **v50** (3.47.0) | 移除「坐标纠偏」开关，纠偏恒定开启 |
| **v49** | 「关于」区移除空壳项；新增「开源仓库」区块 |
| **v48** | 移除「详情」页「车辆信息」区块（v56 曾静默回归，v58 重新落地） |
| **v47** | 补齐「消息通知」「隐私设置」入口 |
| **v46** | 「我的」页车辆卡点击弹出完整车辆信息弹层 |
| **v45** | 首次启动弹出隐私政策，同意前不初始化采集 SDK |
| **v44** | 「我的」页车辆图与首页对齐，手机号改大标题显示 |
| **v43** | 命名统一为「五菱车控」 |
| **v42** | 「我的」页车辆信息卡层级调整 |
| **v41** | 更换启动图标；应用名由占位名改为「五菱车控」 |

</details>

> 📜 更早版本（v40 及以前）见 [**Releases**](https://github.com/daiyuxiang520/geren-update/releases)。

## 免责声明

- 本项目为**个人学习与研究**用途的第三方客户端，**非五菱官方发布**，与上汽通用五菱无任何关联。
- 车辆数据均通过官方 API 获取，本 App 不额外存储、不上传用户的车辆或个人信息；集成的友盟+统计仅采集设备维度匿名数据（可构建时留空 `wuling.umeng.appkey` 关闭）。
- 请遵守相关服务条款，**使用风险自负**；建议仅在自有车辆上使用。如涉及侵权，请联系删除。

## 相关链接

- 📦 [Releases 下载页](https://github.com/daiyuxiang520/geren-update/releases)
- 📄 [更新配置 update.json](./update.json)

---

<sub>最后更新：2026-09-15</sub>
