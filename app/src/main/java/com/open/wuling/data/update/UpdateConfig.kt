package com.open.wuling.data.update

/**
 * 自动更新配置。
 *
 * 托管方式：GitHub 公开仓库 `daiyuxiang520/geren-update`（main 分支）
 *  - update.json 与 APK 均放仓库根目录
 *  - 国内直连 raw.githubusercontent.com 会被污染，故通过 GitHub 公益加速源访问
 *  - 加速源直连 raw，无 CDN 长期缓存问题，改动即时生效
 *
 * 【致谢】加速源列表参考自 XIU2 的开源项目「Github 增强 - 高速下载」
 *  - 项目地址: https://github.com/XIU2/UserScript
 *  - 脚本地址: https://github.com/XIU2/UserScript/blob/master/GithubEnhanced-High-Speed-Download.user.js
 *  本项目从该脚本中提取公益加速源并实测筛选，谨此致谢 (GPL-3.0)。
 *
 * 发布新版本时：替换仓库中的 update.json（versionCode +1、apkUrl/md5 更新）+ 上传新 APK，
 * 推送 main 分支即可；App 侧无需改动、无需重装，启动自动拉取最新版本信息。
 */
object UpdateConfig {

    /** 仓库相对路径（main 分支） */
    private const val RAW =
        "https://raw.githubusercontent.com/daiyuxiang520/geren-update/main"

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

    /** update.json 的候选地址（各加速源拼接 raw 直链） */
    val UPDATE_JSON_URLS: List<String> = MIRRORS.map { "${it.url}$RAW/update.json" }

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
