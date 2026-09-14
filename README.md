# 五菱 App 共存版（Android 16）

> 五菱官方 App 的 **Android 16 共存版**（可与原版并存的独立包），附带桌面小组件、能耗统计、App 内自动更新等增强功能。

[![最新版本](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fghproxy.net%2Fhttps%3A%2F%2Fraw.githubusercontent.com%2Fdaiyuxiang520%2Fgeren-update%2Fmain%2Fupdate.json&query=%24.versionName&label=最新版本&color=blue)](https://github.com/daiyuxiang520/geren-update/releases/latest)
[![Android](https://img.shields.io/badge/Android-16%20(targetSdk%2036)-green)]()
[![包名](https://img.shields.io/badge/包名-com.wuling.app.repack-orange)]()

---

## 简介

本项目是面向 **Android 16（API 36）** 适配的五菱车联网 App 共存版。**共存版**指使用独立包名 `com.wuling.app.repack` 重新签名打包，因此可以与本机原有的五菱官方 App **同时安装、互不冲突**，无需卸载原版。

适用于：官方 App 在 Android 16 上存在兼容问题、或希望在不影响原版的前提下体验增强功能的场景。

## 功能特性

### 核心功能
- **车辆状态**：续航里程、电量、油量、车门/车窗/后备箱状态实时查看
- **车辆控制**：远程上锁/解锁、车窗控制、寻车、空调等
- **车辆详情**：车辆参数、VIN、保养与保险信息
- **位置服务**：车辆定位、轨迹查看，**逆地理地址自动解析**（经纬度 → 中文地址）

### 增强功能
| 功能 | 说明 |
|---|---|
| 📊 **车辆能耗** | 日/月/年三维度能耗统计，点击日期盒弹**日历选择器**，支持里程、油耗、电耗、百公里综合能耗等指标 |
| 🖼️ **桌面小组件** | 2×3 车辆状态卡片：车图 + 总续航大字 + 上电/锁车/车窗三行状态 + 电量/油量双进度条 |
| 🔄 **App 内自动更新** | 启动自动检测新版本，一键下载安装，**无需重新下载 APK** |
| 🎨 **主题设置** | 深色模式、主题色自定义 |
| ⌚ **Apple Watch 同步** | 手表端状态同步配置 |
| 🔋 **系统保活** | 针对各大厂商 ROM 的自启动与后台优化 |

### 技术特性
- 基于 **Jetpack Compose** 的现代化 UI
- **Hilt** 依赖注入
- 针对 **Android 16** 全新适配（16KB 页面对齐、权限模型变更等）
- 支持 **BLE 蓝牙数字钥匙**

## 下载安装

### 最新版本
前往 [**Releases**](https://github.com/daiyuxiang520/geren-update/releases/latest) 页面下载最新的 `wuling-coexist.apk`。

> ⚠️ **下载提示**：GitHub 直链在国内可能无法访问。如遇下载失败，请使用以下 **代理镜像** 地址：
> ```
> https://ghproxy.net/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-coexist.apk
> ```
> （镜像依次可用：`ghproxy.net`、`gh-proxy.com`、`ghfast.top`）

### 安装步骤
1. 下载 APK 文件到手机
2. 点击安装，系统可能提示「未知来源应用」——请在设置中允许**安装未知应用**权限
3. 安装完成后，App 名称为 **「五菱共存版」**，可与原版五菱 App 共存

### 首次使用
- 打开 App 后按提示完成 **登录 / API Token 配置**
- 在「我的」页可查看车辆绑定状态、进行各项设置
- 如需桌面小组件：长按桌面 → 添加小组件 → 找到「车辆状态」

## 自动更新机制

App 内置了自动更新检测，**启动时自动检查**，也可在「我的 → 检查更新」手动触发。

更新信息托管于本仓库的 [`update.json`](./update.json)：

```json
{
  "versionCode": 23,
  "versionName": "3.20.0-android16",
  "updateLog": "...",
  "apkUrl": "https://raw.githubusercontent.com/.../wuling-coexist.apk",
  "forceUpdate": false,
  "md5": "..."
}
```

**检测流程**：App 依次尝试多个 **GitHub 代理镜像**获取 `update.json`（`ghproxy.net` → `gh-proxy.com` → `ghfast.top`），任一成功即返回。若远端 `versionCode` 大于本地，则弹出更新对话框：

1. 点击「立即更新」→ 后台下载 APK
2. 下载完成 → **MD5 校验**（防篡改）
3. 校验通过 → 调起系统安装器
4. 若下载/安装失败 → 自动回退**浏览器下载**

> 采用代理镜像的原因是 `raw.githubusercontent.com`、`api.github.com`、`*.github.io` 在国内均存在 DNS 污染，无法直连。

## 版本历史

| 版本 | 说明 |
|---|---|
| **v23** (3.20.0) | 自动更新改用 GitHub 代理镜像，三路兜底，国内可达 |
| v22 (3.19.0) | 修复更新检测 URL 缺陷 |
| v21 (3.18.0) | 能耗页日历选择器；切维度不重置；顶栏「回到当前时间」 |
| v20 (3.17.0) | 新增 App 内自动更新；桌面小组件重构 |
| v19 (3.16.0) | App 内自动更新框架 |
| v18 (3.15.0) | 修复进度条末端指示点 |
| v17 (3.14.0) | 桌面小组件布局重构（车图 + 双进度条） |
| v16 及以下 | 小组件多轮迭代、能耗功能从零搭建 |

## 免责声明

- 本项目为**个人学习与研究**用途的第三方适配版本，**非五菱官方发布**。
- 所有车辆数据均通过官方 API 获取，本 App 不存储、不上传用户的任何车辆或个人信息。
- 请遵守相关服务条款，**使用风险自负**。如涉及侵权，请联系删除。
- 建议仅在自有车辆上使用，请勿用于商业用途。

## 相关链接

- 📦 [Releases 下载页](https://github.com/daiyuxiang520/geren-update/releases)
- 📄 [更新配置 update.json](./update.json)

---

<sub>最后更新：2026-09-14</sub>
