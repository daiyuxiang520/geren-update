# 五菱助手（Android 16）

> 面向 **Android 16** 的五菱车联网第三方客户端，附带桌面小组件、能耗统计、App 内自动更新等增强功能。

[![最新版本](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fedgeone.gh-proxy.org%2Fhttps%3A%2F%2Fraw.githubusercontent.com%2Fdaiyuxiang520%2Fgeren-update%2Fmain%2Fupdate.json&query=%24.versionName&label=最新版本&color=blue)](https://github.com/daiyuxiang520/geren-update/releases/latest)
[![Android](https://img.shields.io/badge/Android-16%20(targetSdk%2036)-green)]()
[![包名](https://img.shields.io/badge/包名-com.wuling.app.repack-orange)]()

---

## 简介

**五菱助手**是一款面向 Android 16（API 36）的五菱车联网客户端，基于官方 API 重新实现，具备完整独立的车辆管理能力。

它与官方 App **互相独立**：使用独立包名 `com.wuling.app.repack`，拥有自己的桌面图标、自己的设置与数据，**无需依赖官方 App 即可单独使用**。

适用于：官方 App 在 Android 16 上存在兼容问题，或希望获得更丰富的车控与统计功能的场景。

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
| 🖼️ **桌面小组件** | 2×3 车辆状态卡片：车图 + 总续航大字 + 上电/锁车/车窗三行状态 + 电量/油量双进度条，**独立联网刷新**（不依赖 App 进程） |
| 🔄 **App 内自动更新** | 启动自动检测新版本，一键下载安装，**无需重新下载 APK**；内置 **15 个 GitHub 加速源**，下载前**自动测速择优** |
| ⚡ **冷启动秒显** | 车辆数据本地缓存，一进 App 立即显示上次数据，再静默刷新 |
| 🎨 **主题设置** | 深色模式、主题色自定义 |
| 🔋 **系统保活** | 针对各大厂商 ROM 的自启动与后台优化 |
| 📈 **使用统计** | 接入友盟+ U-App，统计启动/设备/留存/**页面浏览**，帮助改进产品（仅设备维度匿名数据） |

### 技术特性
- 基于 **Jetpack Compose** 的现代化 UI
- **Hilt** 依赖注入
- 针对 **Android 16** 全新适配（16KB 页面对齐、权限模型变更等）
- 支持 **BLE 蓝牙数字钥匙**

## 下载安装

### 最新版本
前往 [**Releases**](https://github.com/daiyuxiang520/geren-update/releases/latest) 页面下载最新的 `wuling-assistant.apk`。

> ⚠️ **下载提示**：GitHub 直链在国内可能无法访问。如遇下载失败，请使用以下 **代理镜像** 地址（任选其一替换前面的域名）：
> ```
> https://edgeone.gh-proxy.org/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
> https://ghproxy.net/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
> https://wget.la/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
> ```
> 完整可用镜像列表见下方「自动更新机制」一节。

### 安装步骤
1. 下载 APK 文件到手机
2. 点击安装，系统可能提示「未知来源应用」——请在设置中允许**安装未知应用**权限
3. 安装完成后，桌面显示为 **「五菱助手」**

### 首次使用
- 打开 App 后按提示完成 **登录 / API Token 配置**
- 在「我的」页可查看车辆绑定状态、进行各项设置
- 如需桌面小组件：长按桌面 → 添加小组件 → 找到「车辆状态」

## 自动更新机制

App 内置了自动更新检测，**启动时自动检查**，也可在「我的 → 检查更新」手动触发。

更新信息托管于本仓库的 [`update.json`](./update.json)：

```json
{
  "versionCode": 28,
  "versionName": "3.25.0-android16",
  "updateLog": "...",
  "apkUrl": "https://raw.githubusercontent.com/.../wuling-assistant.apk",
  "forceUpdate": false,
  "md5": "..."
}
```

### 双链路加速策略

**① 检查更新（拉取 update.json）**：依次尝试 **15 个 GitHub 加速源**，上次成功的源会**优先尝试**（记忆在本地）。

**② 下载 APK**：下载前**自动测速择优**——

1. 若上次测速选出的**最快源**仍在候选中，直接使用，**跳过测速**（快到几乎无额外开销）
2. 否则对所有候选源**并发 HEAD 探测**（只读响应头，不下载正文），按响应耗时排序
3. 用**最快**的源开始下载；若下载失败，自动按速度顺序回退到下一个源
4. 下载成功 → **MD5 校验**（防篡改）→ 调起系统安装器
5. 若全部失败 → 自动回退**浏览器下载**

> 之所以要实时测速：公共镜像的延迟波动很大（同一源在不同时段可能从 0.3s 涨到 2.3s），静态排序表会很快过期。实测择优才能保证每次都走最快的路。

### 可用加速源（15 个）

| # | 加速源 | 参考延迟 |
|---|---|---|
| 1 | `edgeone.gh-proxy.org` | 0.20s |
| 2 | `git.yylx.win` | 0.30s |
| 3 | `ghfile.geekertao.top` | 0.53s |
| 4 | `gh.xxooo.cf` | 0.55s |
| 5 | `ghproxy.net` | 0.60s |
| 6 | `ghp.keleyaa.com` | 0.63s |
| 7 | `gitproxy.mrhjx.cn` | 0.63s |
| 8 | `fastgit.cc` | 0.64s |
| 9 | `ghpxy.hwinzniej.top` | 0.66s |
| 10 | `wget.la` | 0.85s |
| 11 | `github.ednovas.xyz` | 0.86s |
| 12 | `cdn.gh-proxy.org` | 0.98s |
| 13 | `github.boki.moe` | 1.01s |
| 14 | `hub.glowp.xyz` | 1.01s |
| 15 | `gh.zwy.one` | 1.37s |

> 「参考延迟」仅为初始排序用，实际下载时会被实时测速结果覆盖。
> 采用加速源的原因是 `raw.githubusercontent.com`、`api.github.com`、`*.github.io` 在国内均存在 DNS 污染，无法直连。

## 开源致谢

本项目的**更新下载能力**依赖以下开源项目，特此致谢：

### 🌟 XIU2/UserScript —「Github 增强 - 高速下载」

- 项目地址：<https://github.com/XIU2/UserScript>
- 脚本地址：<https://github.com/XIU2/UserScript/blob/master/GithubEnhanced-High-Speed-Download.user.js>
- 许可协议：**GPL-3.0**

App 内自动更新所用的 **15 个 GitHub 公益加速源**，提取自该项目脚本中维护的加速源列表，并经本地逐项实测筛选与延迟排序。感谢原作者长期维护这份高质量的加速源清单——没有它，本项目的国内自动更新功能无法实现。

### 其它开源组件

| 组件 | 用途 |
|---|---|
| [Jetpack Compose](https://developer.android.com/jetpack/compose) / AndroidX | UI 框架 |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 异步与并发 |
| [OkHttp](https://github.com/square/okhttp) | 网络请求 |
| [Gson](https://github.com/google/gson) | JSON 序列化（含车辆数据本地缓存） |
| [Hilt](https://dagger.dev/hilt/) | 依赖注入 |
| [Coil](https://github.com/coil-kt/coil) | 图片加载 |

## 版本历史

| 版本 | 说明 |
|---|---|
| **v28** (3.25.0) | 接入**友盟+ U-App 使用统计**（启动/设备/留存/页面浏览）；新增 `local.properties` 的 `wuling.umeng.appkey` 配置项 |
| **v27** (3.24.0) | **更名为「五菱助手」**，明确独立 App 定位；下载更新改为**自动测速择优**并记忆最快源；移除手表同步入口 |
| v26 (3.23.0) | 加速源扩展至 **15 个**；新增**成功源记忆**；App 内新增「关于我们」开源致谢页 |
| v25 (3.22.0) | 冷启动秒显：车辆数据本地缓存（Gson 序列化），进 App 即有数据 |
| v24 (3.21.0) | 桌面小组件独立联网刷新，不再依赖 App 进程（30 分钟自动更新） |
| v23 (3.20.0) | 自动更新改用 GitHub 代理镜像，多路兜底，国内可达 |
| v22 (3.19.0) | 修复更新检测 URL 缺陷 |
| v21 (3.18.0) | 能耗页日历选择器；切维度不重置；顶栏「回到当前时间」 |
| v20 (3.17.0) | 新增 App 内自动更新；桌面小组件重构 |
| v19 (3.16.0) | App 内自动更新框架 |
| v18 (3.15.0) | 修复进度条末端指示点 |
| v17 (3.14.0) | 桌面小组件布局重构（车图 + 双进度条） |
| v16 及以下 | 小组件多轮迭代、能耗功能从零搭建 |

## 免责声明

- 本项目为**个人学习与研究**用途的第三方客户端，**非五菱官方发布**，与上汽通用五菱无任何关联。
- 车辆数据均通过官方 API 获取，本 App 本身不存储、不上传用户的车辆或个人信息。
- **使用统计**：App 集成了友盟+（Umeng）U-App 统计 SDK，会采集**设备维度**的匿名信息（如设备型号、系统版本、启动与页面浏览行为）用于产品改进，数据按[友盟隐私政策](https://www.umeng.com/page/policy)处理。不使用该统计可在构建时留空 `wuling.umeng.appkey`。
- 请遵守相关服务条款，**使用风险自负**。如涉及侵权，请联系删除。
- 建议仅在自有车辆上使用，请勿用于商业用途。

## 相关链接

- 📦 [Releases 下载页](https://github.com/daiyuxiang520/geren-update/releases)
- 📄 [更新配置 update.json](./update.json)
- 🙏 [XIU2/UserScript（加速源来源）](https://github.com/XIU2/UserScript)

---

<sub>最后更新：2026-09-14</sub>
