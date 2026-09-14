# 五菱助手 · Wuling Assistant

> 面向 **Android 16（API 36）** 的五菱新能源车联网**开源客户端**。独立运行，无需 Home Assistant，直接对接五菱官方车联网接口。

[![Android](https://img.shields.io/badge/Android-16%20(API%2036)-green)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)]()
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](./LICENSE)
[![最新版本](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fedgeone.gh-proxy.org%2Fhttps%3A%2F%2Fraw.githubusercontent.com%2Fdaiyuxiang520%2Fgeren-update%2Fmain%2Fupdate.json&query=%24.versionName&label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=blue)](https://github.com/daiyuxiang520/geren-update/releases/latest)

---

> 本仓库**同时承担两个角色**：① **更新分发仓库** —— 根目录的 `update.json` + `wuling-assistant.apk` 是 App 自动更新的数据源；② **源码仓库** —— 下文「源码构建」节可照此从零编译。两者互不干扰。

## 📱 下载安装（普通用户）

### 最新版本
前往 [**Releases**](https://github.com/daiyuxiang520/geren-update/releases/latest) 页面下载最新的 `wuling-assistant.apk`。

> ⚠️ **下载提示**：GitHub 直链在国内可能无法访问。如遇下载失败，请使用以下 **代理镜像** 地址（任选其一替换前面的域名）：
> ```
> https://edgeone.gh-proxy.org/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
> https://ghproxy.net/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
> https://wget.la/https://raw.githubusercontent.com/daiyuxiang520/geren-update/main/wuling-assistant.apk
> ```

### 安装步骤
1. 下载 APK 文件到手机
2. 点击安装，系统可能提示「未知来源应用」——请在设置中允许**安装未知应用**权限
3. 安装完成后，桌面显示为 **「五菱助手」**（包名 `com.wuling.app.repack`）

### 首次使用
- 打开 App 后按提示完成 **登录 / API Token 配置**
- 在「我的」页可查看车辆绑定状态、进行各项设置
- 如需桌面小组件：长按桌面 → 添加小组件 → 找到「车辆状态」

## 简介

**五菱助手**是一款开源的五菱新能源车控 App，为车主提供车辆状态监控、远程控制、能耗统计等一站式功能。

项目基于 [hasscc/wuling](https://github.com/hasscc/wuling) 的思路独立实现，但**不依赖 Home Assistant**，也不依赖官方 App——安装后按指引配置即可单独使用。

## 功能特性

### 核心功能
| 功能 | 说明 |
|---|---|
| 🔧 **远程车控** | 解锁/上锁、远程空调（含温度与风速）、开启尾箱、寻车鸣笛、车窗控制 |
| 📊 **状态监控** | 电量(SOC)、电池健康(SOH)、剩余续航、总里程、昨日里程、平均能耗、电池温度、电机温度、低压电池 |
| 🗺️ **位置服务** | 车辆实时定位、一键导航找车，**逆地理地址解析**（经纬度 → 中文地址） |
| 📈 **能耗统计** | 日 / 月 / 年三维度，点击日期盒弹**日历选择器**，支持里程、油耗、电耗、百公里综合能耗 |

### 增强功能
| 功能 | 说明 |
|---|---|
| 🖼️ **桌面小组件** | 2×3 车辆状态卡片，**独立联网刷新**（不依赖 App 进程） |
| 🔄 **自动更新** | 启动检测新版本，一键下载安装；内置多加速源，下载前**自动测速择优** |
| ⚡ **冷启动秒显** | 车辆数据本地缓存，进 App 立即显示上次数据，再静默刷新 |
| 🎨 **主题设置** | 深色模式、主题色自定义 |
| 🔋 **系统保活** | 针对各大厂商 ROM（ColorOS / MIUI / HarmonyOS 等）的自启动引导 |
| 🔑 **BLE 数字钥匙** | 蓝牙近场控车 |

### 技术栈
- **Jetpack Compose** + Material3
- **Hilt** 依赖注入
- **OkHttp** 网络层（含请求签名拦截器）
- **Gson** 序列化
- **Coil** 图片加载
- **DataStore** 持久化

## 自动更新机制

App 内置了自动更新检测，**启动时自动检查**，也可在「我的 → 检查更新」手动触发。

更新信息托管于本仓库的 [`update.json`](./update.json)。App 通过 **15 个 GitHub 公益加速源** 访问该文件（国内直连 `raw.githubusercontent.com` 会被 DNS 污染）。

### 双链路加速策略

**① 检查更新（拉取 update.json）**：依次尝试 **15 个加速源**，上次成功的源会**优先尝试**（记忆在本地）。

**② 下载 APK**：下载前**自动测速择优**——
1. 若上次测速选出的**最快源**仍在候选中，直接使用，**跳过测速**（快到几乎无额外开销）
2. 否则对所有候选源**并发 HEAD 探测**（只读响应头，不下载正文），按响应耗时排序
3. 用**最快**的源开始下载；若下载失败，自动按速度顺序回退到下一个源
4. 下载成功 → **MD5 校验**（防篡改）→ 调起系统安装器
5. 若全部失败 → 自动回退**浏览器下载**

> 之所以要实时测速：公共镜像的延迟波动很大（同一源在不同时段可能从 0.3s 涨到 2.3s），静态排序表会很快过期。实测择优才能保证每次都走最快的路。

### 可用加速源（15 个，参考延迟）

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

## 源码构建（开发者）

### 1. 环境要求

| 项目 | 版本 |
|---|---|
| JDK | 17+ |
| Android SDK | API 36 (Android 16) |
| Gradle | 由 `gradlew` 自动管理 |

### 2. 配置

复制配置模板并填写：

```bash
cp local.properties.example local.properties
```

然后编辑 `local.properties`。其中：

- **固定值项**（`client.id`、`base.url`、`api.version` 等）已预填，可直接使用
- **凭据项**（`*.secret`、`*.imei`、`*.llb.*`）需要自行获取后填入

> ⚠️ **重要**：本仓库**不包含任何接口凭据**。所有密钥类配置均需自行提供，缺失时相关功能不可用（不影响编译）。
> `local.properties` 已被 `.gitignore` 排除，不会被提交。

### 3. 构建

```bash
# 调试版
./gradlew assembleDebug

# 发布版（需先配置签名环境变量，见下）
./gradlew assembleRelease
```

产物路径：`app/build/outputs/apk/`

### 4. 签名配置（仅发布版需要）

签名信息通过**环境变量**提供，源码中不包含任何口令：

```bash
export WULING_KEYSTORE_PATH=/path/to/your.keystore
export WULING_KEYSTORE_PASSWORD=your_store_password
export WULING_KEY_ALIAS=your_key_alias
export WULING_KEY_PASSWORD=your_key_password
```

生成自己的 keystore：

```bash
keytool -genkeypair -v -keystore my.keystore -alias mykey \
        -keyalg RSA -keysize 2048 -validity 10000
```

> 未配置签名环境变量时，release 构建会**自动回退到 debug 签名**，仅用于本地验证，不可用于分发。

## 项目结构

```
app/src/main/java/com/open/wuling/
├── data/
│   ├── api/          # 网络层：接口定义、签名、配置
│   ├── local/        # 本地存储：偏好设置、地址解析
│   ├── model/        # 数据模型
│   ├── repository/   # 数据仓库
│   ├── store/        # DataStore 封装
│   └── update/       # 自动更新（多源测速）
├── ui/
│   ├── components/   # 复用组件
│   ├── screens/      # 页面
│   └── theme/        # 主题
├── ble/              # 蓝牙数字钥匙
├── widget/           # 桌面小组件
├── oem/              # 厂商 ROM 兼容
└── util/             # 工具类
```

## 开源致谢

### 🌟 XIU2/UserScript —「Github 增强 - 高速下载」

- 项目地址：<https://github.com/XIU2/UserScript>
- 脚本地址：<https://github.com/XIU2/UserScript/blob/master/GithubEnhanced-High-Speed-Download.user.js>
- 许可协议：**GPL-3.0**

App 内自动更新所用的 **15 个 GitHub 公益加速源**，提取自该项目脚本中维护的加速源列表，并经本地逐项实测筛选。感谢原作者长期维护这份高质量的加速源清单。

### 其它

| 组件 | 用途 |
|---|---|
| [hasscc/wuling](https://github.com/hasscc/wuling) | 项目思路来源 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) / AndroidX | UI 框架 |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 异步与并发 |
| [OkHttp](https://github.com/square/okhttp) | 网络请求 |
| [Gson](https://github.com/google/gson) | JSON 序列化 |
| [Hilt](https://dagger.dev/hilt/) | 依赖注入 |
| [Coil](https://github.com/coil-kt/coil) | 图片加载 |
| [高德地图](https://lbs.amap.com/) | 地图与逆地理编码 |

## 适配车型

- **核心适配**：五菱星光（含共创版等全系）
- **兼容支持**：五菱新能源全系（基于通用车联网接口，可自行测试）

## 参与贡献

欢迎提交 Issue 反馈问题、提出建议，也欢迎 PR 共同完善：

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/xxx`）
3. 提交改动（`git commit -m 'feat: xxx'`）
4. 推送分支（`git push origin feature/xxx`）
5. 发起 Pull Request

> 提交前请确认**没有把 `local.properties` 或任何密钥文件加入版本控制**。

## 版本历史

| 版本 | 说明 |
|---|---|
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

1. 本项目为**非官方第三方开源工具**，与上汽通用五菱**无任何隶属或合作关系**，仅供个人学习与研究使用。
2. 所有车辆数据均直接与官方接口交互，**本 App 不存储、不上传用户的任何账号、车辆或个人隐私信息**。
3. 「五菱」「五菱星光」等为五菱汽车官方注册商标，本项目仅为工具类应用，不主张任何相关权利。
4. **使用风险自负**。请遵守相关服务条款，禁止用于商业用途。如涉及侵权，请联系删除。

## 相关链接

- 📦 [Releases 下载页](https://github.com/daiyuxiang520/geren-update/releases)
- 📄 [更新配置 update.json](./update.json)
- 🙏 [XIU2/UserScript（加速源来源）](https://github.com/XIU2/UserScript)

---

<sub>本项目不隶属于上汽通用五菱。请勿将车辆控制功能用于非法用途。</sub>
