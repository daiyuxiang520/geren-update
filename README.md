# 五菱车控（Android 16）

> 面向 **Android 16** 的五菱车联网第三方客户端，附带桌面小组件、能耗统计、App 内自动更新等增强功能。

[![最新版本](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fedgeone.gh-proxy.org%2Fhttps%3A%2F%2Fraw.githubusercontent.com%2Fdaiyuxiang520%2Fgeren-update%2Fmain%2Fupdate.json&query=%24.versionName&label=最新版本&color=blue)](https://github.com/daiyuxiang520/geren-update/releases/latest)
[![Android](https://img.shields.io/badge/Android-16%20(targetSdk%2036)-green)]()
[![包名](https://img.shields.io/badge/包名-com.wuling.app.repack-orange)]()

---

## 简介

**五菱车控**是一款面向 Android 16（API 36）的五菱车联网客户端，基于官方 API 重新实现，具备完整独立的车辆管理能力。

它与官方 App **互相独立**：使用独立包名 `com.wuling.app.repack`，拥有自己的桌面图标、自己的设置与数据，**无需依赖官方 App 即可单独使用**。

适用于：官方 App 在 Android 16 上存在兼容问题，或希望获得更丰富的车控与统计功能的场景。

## 功能特性

### 核心功能
- **车辆状态**：续航里程、电量、油量、车门/车窗/后备箱状态实时查看
- **车辆控制**：远程上锁/解锁、车窗控制、寻车、空调等
- **车辆详情**：车辆参数、VIN、保养与保险信息
- **位置服务**：车辆定位、轨迹查看，**逆地理地址自动解析**（经纬度 → 中文地址），并显示**所在地区天气**（天气现象/温度/湿度/风力）

### 增强功能
| 功能 | 说明 |
|---|---|
| 📊 **车辆能耗** | 日/月/年三维度能耗统计，点击日期盒弹**日历选择器**，支持里程、油耗、电耗、百公里综合能耗等指标 |
| 🖼️ **桌面小组件** | 2×3 车辆状态卡片：车图 + 总续航大字 + 上电/锁车/车窗三行状态 + 电量/油量双进度条，**独立联网刷新**（不依赖 App 进程） |
| 🔄 **App 内自动更新** | 启动自动检测新版本，一键下载安装，**无需重新下载 APK**；内置 **15 个 GitHub 加速源**，下载前**自动测速择优**；支持**手动选择加速源**（实时查看各源延迟） |
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
3. 安装完成后，桌面显示为 **「五菱车控」**

### 首次使用
- 打开 App 后按提示完成 **登录 / API Token 配置**
- 在「我的」页可查看车辆绑定状态、进行各项设置
- 如需桌面小组件：长按桌面 → 添加小组件 → 找到「车辆状态」

## 自动更新机制

App 内置了自动更新检测，**启动时自动检查**，也可在「我的 → 检查更新」手动触发。

更新信息托管于本仓库的 [`update.json`](./update.json)：

```json
{
  "versionCode": 38,
  "versionName": "3.35.0-android16",
  "updateLog": "...",
  "apkUrl": "https://raw.githubusercontent.com/.../wuling-assistant.apk",
  "forceUpdate": false,
  "md5": "..."
}
```

### 双链路加速策略

**① 检查更新（拉取 update.json）**：**并发**请求 **15 个 GitHub 加速源**、取其中**版本号最大**的结果（避免某个源缓存旧版导致永远提示「已是最新」），上次成功的源会**优先尝试**（记忆在本地）。

**② 下载 APK**：下载前**自动测速择优**——

1. 若上次测速选出的**最快源**仍在候选中，直接使用，**跳过测速**（快到几乎无额外开销）
2. 否则对所有候选源**并发 HEAD 探测**（只读响应头，不下载正文），按响应耗时排序
3. 用**最快**的源开始下载；若下载失败，自动按速度顺序回退到下一个源
4. 下载成功 → **MD5 校验**（防篡改）→ 调起系统安装器
5. 若全部失败 → 自动回退**浏览器下载**

### 手动选择下载源

如果你对某个加速源更放心、或想跳过测速直接指定，更新弹窗里可以**手动选择**：

- 点击弹窗中的「**下载源 → 选择**」展开源列表，每项实时显示**当前延迟**（`快 / 一般 / 慢 / 不可用`）
- 默认是「**自动（推荐）**」：沿用上面的自动测速择优
- 选中某个源后点击「立即更新」，本次下载**只走该源**（绕过自动排序）
- 手动选择**仅本次更新流程生效**，下次弹窗自动回到「自动」——不会覆盖你长期的最优记忆

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

本项目在车控协议理解、更新下载能力上受益于以下开源项目，特此致谢：

### 🌟 hasscc/wuling — 车控协议思路来源

- 项目地址：<https://github.com/hasscc/wuling>
- 许可协议：**MIT**

本项目的**车控协议与接口思路**参考自 hasscc 的 Home Assistant 集成项目 `wuling`。本应用在此基础上以**原生 Android 独立实现**——不依赖 Home Assistant，也不依赖官方 App，安装后按指引配置即可单独使用。感谢原作者对五菱车联网接口的开源探索。

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
| [AndroidX DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | Token 等偏好持久化 |
| [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) | 本地加密存储 |
| [友盟+ U-App](https://www.umeng.com/) | 使用统计（启动/设备/留存/页面浏览） |

## 构建

本项目使用 Gradle + Android Gradle Plugin 构建。**仓库不含任何密钥**，所有敏感配置通过 `local.properties` 或环境变量注入（见 `app/build.gradle.kts` 中的 `prop()` 读取逻辑）。

### 环境要求
- **JDK 17**
- **Android SDK**：`compileSdk` / `targetSdk` = 36（Android 16）
- 联网环境（首次构建会下载 Gradle 及依赖；国内网络可参考 `build-env/mirrors.gradle` 注入仓库镜像）

### 1. 配置接口凭据
```bash
cp local.properties.example local.properties
```
按 `local.properties.example` 内注释逐项填写。其中 `client.id` / `app.code` / `base.url` / `api.version` 等为固定值可直接使用；`client.secret`、`llb.*`、`energy.*` 为车联网接口凭据，需自行获取（缺失时对应功能不可用，但 App 仍可正常构建运行）。

> `local.properties` 已被 `.gitignore` 排除，不会进入版本库。

### 2. 配置签名（发布用）
release 构建的签名**完全来自环境变量**，源码中不内置任何口令：
```bash
export WULING_KEYSTORE_PATH=/path/to/your.keystore
export WULING_KEYSTORE_PASSWORD=your_store_password
export WULING_KEY_ALIAS=your_key_alias
export WULING_KEY_PASSWORD=your_key_password
```
未设置时，release 构建会自动回退到 **debug 签名**（仅用于本地验证，无法覆盖安装正式版）。生成自己的签名密钥：
```bash
keytool -genkeypair -v -keystore my.keystore -alias mykey \
        -keyalg RSA -keysize 2048 -validity 10000
```

### 3. 构建
```bash
# 方式 A：本机直接构建
./gradlew assembleRelease          # 产物位于 app/build/outputs/apk/release/

# 方式 B：使用预置 Docker 镜像（已含 SDK 36 + 国内镜像，推荐）
./build-android16.sh               # 构建 release
./build-android16.sh debug         # 构建 debug
```

### 4. 对齐与签名（手动签名时）
```bash
zipalign -f 4 app-release.apk app-release-aligned.apk
apksigner sign --ks "$WULING_KEYSTORE_PATH" \
               --ks-key-alias "$WULING_KEY_ALIAS" app-release-aligned.apk
```

> 应用包名固定为 `com.wuling.app.repack`，构建出的 APK 可直接覆盖安装历史版本。

## 版本历史

| 版本 | 说明 |
|---|---|
| **v59** (3.56.0) | **「调试日志」全量重做**：五菱 API 请求/响应真正接入日志（此前 release 包拦截器为 NONE，面板里看不到任何 API 报文），能耗 API 同步接入；敏感信息脱敏（密码/secret 打码、token 留前 6 位、手机号留前 3 后 4、VIN 留后 4）；日志**按天落盘持久化**（重启可查，自动保留 3 天/2MB），「记录」开关重启保持；面板新增等级 D/I/W/E 筛选、Tag 筛选、关键词搜索、每秒自动刷新、单条点击复制、详情展开、**导出分享**；蓝牙钥匙日志并轨（tag=BLE） |
| **v58** (3.55.0) | **详情页移除「车辆信息」区块，首屏直达实时状态**：进入「详情」页第一眼即电池、车门、车窗等动态信息，不再被 18 行静态档案占据首屏。该区块 v48 已删除过，因工作区文件被旧副本覆盖而在 v56 静默回归（经反汇编 v46/v48/v49/v56 四个历史 APK 的字节码确认），本次重新落地；车辆静态档案仍从「我的」页点击顶部车辆卡查看，字段完全一致。**自本版起，源码改动随每次发布提交进本仓库**，杜绝再次发生静默回退 |
| **v57** (3.54.0) | **首页状态行不再折行**：移除「快捷控制」标题（该组按钮靠图标+文字已自解释），车辆状态行改为独占整行。此前状态行被挤在标题右侧约 1/3 宽度里，四个维度必然折行，「车窗全关」还会被劈成两行。同时移除详情页顶部的上/下电横幅，避免同一信息两处重复 |
| **v56** (3.53.0) | **上电/下电状态全 App 打通**：快捷控制状态行升级为「上电/下电 · 已锁/未锁 · 空调开启/关闭 · 车窗全关/未关」；「启动」按钮按真实状态切换（下电显示「启动」，上电显示「已上电」并转绿，此前点了没有任何反馈）；详情页「驾驶状态」新增「整机状态」行。统一到 `FormatUtils.isPowerOn / getPowerStatusText`，消除首页、详情页、桌面小组件三处各写一套 `keyStatus == "2"` 的隐患 |
| **v55** (3.52.0) | **能耗趋势图可点击查看明细**：点柱弹出该周期完整明细（里程、百公里电耗/油耗、行程数、行驶天数）；**新增「能量构成」饼图**，按 1 L 汽油 = 3.0 kWh 折算为统一能量单位后成饼（单位不同不能直接相加，标题标注「已折算」）；日/月/年三维度全支持。**修复日维度 X 轴标签错乱**：原按数组下标抽稀，显示成「0 0 11 1 2 2」，现按日期值抽稀，正确显示 1/5/10/15/20/25/30 |
| **v54** (3.51.0) | **能耗数据页新增「能耗趋势」图表**：柱状图 + 指标切换；月/年维度为近 12 个月逐月趋势，可切换查看里程；日维度为当月逐日趋势，并附油耗折线图 |
| **v53** | **首页「快捷控制」状态行新增车窗状态**：显示「车窗全关」或「车窗未关」，未关时转橙色并加重字重，一眼看出有窗没关。判定同时参考四窗状态位与开度，两者不一致时按「未关」处理，避免漏报 |
| **v52** | **位置页地图预览图同步升级为 3D**：与全屏地图观感统一；预览图采用较小俯仰角（35°），窄幅尺寸下画面更清晰、车点更好辨认；保持静态不响应手势，页面滑动依旧顺畅 |
| **v51** (3.50.0) | **全屏地图升级为 3D 视图**：显示立体楼块，支持俯仰（0-83°）与旋转手势；新增罗盘控制盘（ControlBar），可直观调整朝向与倾角；设备不支持 WebGL 时自动回落 2D，绝不空白 |
| **v50** (3.47.0) | **移除「位置」页的「坐标纠偏 (WGS84 → 高德)」开关**，纠偏改为恒定开启。普通用户无需理解坐标系差异，也不会再因误关开关导致车点偏移 200~600 米；位置页随之减去约 76dp 高度，滚动距离更短 |

<details>
<summary>点击展开 v41 ~ v49（可折叠）</summary>

| 版本 | 说明 |
|---|---|
| **v49** | 「关于」区移除点击无反应的「用户协议」「隐私政策」空壳项（隐私政策统一由「设置 → 隐私设置」提供，那里还含权限状态管理与数据清理）；新增「开源仓库」区块，一键跳转 GitHub 仓库 |
| **v48** | 移除「详情」页的「车辆信息」区块（原占据首屏 18 行静态档案），内容已在 v46 迁移至「我的」页——点击顶部车辆卡即可查看，两处不再重复 |
| **v47** | 补齐「我的」页「消息通知」「隐私设置」两个空壳入口：实时展示通知权限状态（未开启时引导跳转系统设置）、展示各通知渠道状态 |
| **v46** | 「我的」页顶部车辆卡片支持点击，弹出完整的「车辆信息」详情弹层，无需切换到「详情」Tab；车辆卡右侧新增 **›** 箭头提示可点击 |
| **v45** | 首次启动弹出「隐私政策与用户协议」，同意后才能进入应用；同意前不初始化任何采集 SDK，符合工信部《APP 用户权益保护测评规范》上架要求；同意状态本地持久化，仅首启弹一次 |
| **v44** | 「我的」页车辆图与首页对齐（圆角方框、完整显示整车、去掉蓝色圆形底圈）；移除手机号前图标，手机号改大标题显示；删除无效的「编辑」按钮，手机号列更宽不再被截断 |
| **v43** | 命名统一：App「关于」页与开源 README 统一为「五菱车控」，此前残留「五菱助手」字样。仅文案修正，功能无变化 |
| **v42** | 「我的」页车辆信息卡层级调整：上半行 = 绑定手机号（📱 大标题），下半行 = 车系名；删除原底部 VIN 行；去掉挤窄文字区的多余 spacer，手机号不再被「编辑」按钮截断 |
| **v41** | 更换启动图标为新设计「电动车 + 四个功能徽标（锁/电池/车/充电）」自适应图标（自动适配各 OEM 蒙版形状）；应用名称由占位名 `50third` 改为「五菱车控」 |

</details>

> 📜 **更早版本**（v40 及以前）请见 [**Releases 页面**](https://github.com/daiyuxiang520/geren-update/releases)。

## 免责声明

- 本项目为**个人学习与研究**用途的第三方客户端，**非五菱官方发布**，与上汽通用五菱无任何关联。
- 车辆数据均通过官方 API 获取，本 App 本身不存储、不上传用户的车辆或个人信息。
- **使用统计**：App 集成了友盟+（Umeng）U-App 统计 SDK，会采集**设备维度**的匿名信息（如设备型号、系统版本、启动与页面浏览行为）用于产品改进，数据按[友盟隐私政策](https://www.umeng.com/page/policy)处理。不使用该统计可在构建时留空 `wuling.umeng.appkey`。
- 请遵守相关服务条款，**使用风险自负**。如涉及侵权，请联系删除。
- 建议仅在自有车辆上使用，请勿用于商业用途。

## 相关链接

- 📦 [Releases 下载页](https://github.com/daiyuxiang520/geren-update/releases)
- 📄 [更新配置 update.json](./update.json)
- 🙏 [hasscc/wuling（车控协议思路来源）](https://github.com/hasscc/wuling)
- 🙏 [XIU2/UserScript（加速源来源）](https://github.com/XIU2/UserScript)

---

<sub>最后更新：2026-09-15</sub>
