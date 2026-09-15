package com.open.wuling.data.update

/**
 * 自动更新配置。
 *
 * 托管方式：两地同名仓库 `daiyuxiang520/geren-update`，源码一致
 *  - **Gitee（国内首选）**：master 分支，update.json 直连可达；APK 走「发行版」附件
 *  - **GitHub（海外/兜底）**：main 分支，update.json 与 APK 均放根目录，经公益加速源访问
 *  - 两份 update.json 的 versionCode 同步，apkUrl 各自指向己方托管，链路互不依赖
 *
 * 【致谢】加速源列表参考自 XIU2 的开源项目「Github 增强 - 高速下载」
 *  - 项目地址: https://github.com/XIU2/UserScript
 *  - 脚本地址: https://github.com/XIU2/UserScript/blob/master/GithubEnhanced-High-Speed-Download.user.js
 *  本项目从该脚本中提取公益加速源并实测筛选，谨此致谢 (GPL-3.0)。
 *
 * 发布新版本时（两地同步，App 侧无需改动、无需重装，启动即拉取最新版本信息）：
 *  1. GitHub：替换根目录 update.json（versionCode +1）+ 上传 APK，推 main 分支
 *  2. Gitee：推 master 分支（源码 + update.json），APK 发行版附件方式上传
 *  3. 两份 update.json 的 versionCode 必须一致——并发取最大值，不一致会出现版本回退
 */
object UpdateConfig {

    /** 仓库相对路径（main 分支） */
    private const val RAW =
        "https://raw.githubusercontent.com/daiyuxiang520/geren-update/main"

    /**
     * Gitee 国内镜像仓库相对路径（master 分支）。
     *
     * 国内可直连、无需任何第三方代理，是首要版本清单来源。
     * 其 update.json 内的 apkUrl 指向 Gitee 发行版附件，同样免代理下载。
     */
    private const val GITEE_RAW =
        "https://gitee.com/daiyuxiang520/geren-update/raw/master"

    /**
     * GitHub 公益加速源（国内可达），按实测延迟从快到慢排序。
     *
     * 列表扩到 15 个：单一源失效/限流不影响整体可用性。
     * fetchUpdateInfo 会**并发**请求全部源并取 versionCode 最大的结果——
     * 加速源是第三方 CDN，返回旧缓存副本时也能靠其它已刷新的源纠正
     * （顺序遍历「首个成功即用」会被陈旧缓存卡住，导致检测不到新版本）。
     */
    /**
     * 加速源条目：url 为代理前缀，label 为 UI 展示名（取域名，便于用户辨识）。
     * 顺序即「实测从快到慢」，UI 列表与下载测速均沿用此顺序。
     */
    data class MirrorEntry(val label: String, val url: String)

    private val MIRRORS = listOf(
        MirrorEntry("edgeone.gh-proxy.org", "https://edgeone.gh-proxy.org/"),     // 实测 0.20s
        MirrorEntry("git.yylx.win", "https://git.yylx.win/"),                       // 实测 0.30s
        MirrorEntry("ghfile.geekertao.top", "https://ghfile.geekertao.top/"),     // 实测 0.53s
        MirrorEntry("gh.xxooo.cf", "https://gh.xxooo.cf/"),                         // 实测 0.55s
        MirrorEntry("ghproxy.net", "https://ghproxy.net/"),                         // 实测 0.60s
        MirrorEntry("ghp.keleyaa.com", "https://ghp.keleyaa.com/"),               // 实测 0.63s
        MirrorEntry("gitproxy.mrhjx.cn", "https://gitproxy.mrhjx.cn/"),           // 实测 0.63s
        MirrorEntry("fastgit.cc", "https://fastgit.cc/"),                           // 实测 0.64s
        MirrorEntry("ghpxy.hwinzniej.top", "https://ghpxy.hwinzniej.top/"),       // 实测 0.66s
        MirrorEntry("wget.la", "https://wget.la/"),                                 // 实测 0.85s（APK 下载最快）
        MirrorEntry("github.ednovas.xyz", "https://github.ednovas.xyz/"),         // 实测 0.86s
        MirrorEntry("cdn.gh-proxy.org", "https://cdn.gh-proxy.org/"),             // 实测 0.98s
        MirrorEntry("github.boki.moe", "https://github.boki.moe/"),               // 实测 1.01s
        MirrorEntry("hub.glowp.xyz", "https://hub.glowp.xyz/"),                   // 实测 1.01s
        MirrorEntry("gh.zwy.one", "https://gh.zwy.one/")                            // 实测 1.37s
    )

    /**
     * update.json 的候选地址：**Gitee 直连优先**，其后是各加速源拼接的 raw 直链。
     *
     * Gitee 排在最前不是形式主义：fetchUpdateInfo 并发请求全部源、取 versionCode
     * 最大的结果，且**版本相同时保留先返回者**。国内 Gitee 往返通常几十毫秒，
     * 远快于任一公益加速源，自然胜出 → apkUrl 取 Gitee 发行版附件地址 → 全程免代理。
     * 海外用户反之，GitHub 加速源先到，走原本的下发路径，行为同样正确。
     *
     * 15 个 GitHub 加速源**全部保留**作为兜底：Gitee 不可用时仍能检测到版本并完成下载。
     */
    val UPDATE_JSON_URLS: List<String> =
        listOf("$GITEE_RAW/update.json") + MIRRORS.map { "${it.url}$RAW/update.json" }

    /** 兼容旧调用：默认使用第一个加速源 */
    val UPDATE_JSON_URL: String get() = UPDATE_JSON_URLS.first()

    /** 把 raw 直链转换为各加速源代理地址（供 update.json 内的 apkUrl 兜底用） */
    fun mirrorCandidates(rawUrl: String): List<String> {
        if (!rawUrl.startsWith("https://raw.githubusercontent.com/")) return listOf(rawUrl)
        return MIRRORS.map { "${it.url}$rawUrl" } + rawUrl
    }

    /** 供 UI 展示的加速源列表（只读快照，label 为展示名） */
    val MIRRORS_PUBLIC: List<MirrorEntry> get() = MIRRORS
}
