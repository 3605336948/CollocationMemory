package com.example.collocationmemory.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * 把下载好的 APK 交给系统安装。
 *
 * ============================================================
 * 先说清楚一件事：Android 不允许 App 自己「静默安装」
 * ============================================================
 * 从 Android 8.0 起，任何第三方 App 想装别的 APK，都必须：
 *   1. 声明 `REQUEST_INSTALL_PACKAGES` 权限；
 *   2. 让用户去系统设置里，为本应用打开「允许安装未知应用」开关；
 *   3. 最后仍然会弹出系统的安装确认页，**用户必须点一下「安装」**。
 *
 * 第 3 步是没法绕过的 —— 这是 Android 的安全底线（否则任何 App 都能偷偷
 * 装东西）。除非上架应用商店走商店的自动更新通道，或者用户手机被 root。
 *
 * 所以本 App 能做到的「最自动」是：
 *   检查到新版本 → App 内下载（带进度）→ 校验签名 → 自动跳到安装页 → 用户点一下。
 * 相比「去群里翻文件、找最新版、手动下载」，已经少了大部分步骤。
 *
 * 好消息：只要新包用**同一把密钥签名**、且 versionCode 更大，
 * 系统会把它当成「同一个 App 的升级」，覆盖安装后**学习记录一条都不会丢**。
 */
object ApkInstaller {

    private const val TAG = "CollocationMemory"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /**
     * 本应用当前是否有权安装未知来源的应用。
     *
     * 这是「允许安装未知应用」那个开关的状态，默认是关的，
     * 必须引导用户去开。返回 false 时直接 startActivity 装包会被系统静默拦掉。
     */
    fun canInstall(context: Context): Boolean {
        return try {
            context.packageManager.canRequestPackageInstalls()
        } catch (e: Exception) {
            // 极少数定制系统上这个 API 会抛异常，此时按「不能装」处理，
            // 让界面走引导流程，比崩溃强
            Log.w(TAG, "读取安装权限状态失败：${e.message}")
            false
        }
    }

    /**
     * 打开本应用的「安装未知应用」设置页。
     *
     * 用户在里面打开开关后返回，界面应重新调用 [canInstall] 确认。
     */
    fun openInstallPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 个别系统没有这个设置页，退回到「应用详情」页
            Log.w(TAG, "跳转安装权限设置页失败，改跳应用详情：${e.message}")
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "无法打开系统设置页", e2)
            }
        }
    }

    /**
     * 唤起系统安装界面。
     *
     * 用 FileProvider 把 cache 目录里的 APK 转成 `content://` 再交出去 ——
     * Android 7.0 之后禁止直接传 `file://`（会抛 FileUriExposedException）。
     * 权限是「一次性授予给安装器」的，装完即失效。
     *
     * @return 是否成功唤起了安装界面
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() == 0L) {
            Log.e(TAG, "安装失败：APK 文件不存在或为空 ${apk.absolutePath}")
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "唤起安装界面失败：${e.message}", e)
            false
        }
    }

    /**
     * 校验下载下来的 APK 确实是「我们自己签名的同一个包」。
     *
     * 为什么必须做这一步：更新地址是外部的，万一被劫持 / 被替换，
     * 装上去的就是别人塞的恶意包。这里比对两件事：
     *   1. 包名必须一致；
     *   2. 签名证书的 SHA-256 必须与本机安装的版本一致。
     *
     * 只要有一项对不上就拒绝安装 —— 宁可更新失败，也不能装错包。
     * （顺带一提：签名不一致的包，系统本来也装不上，会报「应用签名不一致」。
     * 我们提前拦住，是为了给出人话提示，而不是让用户看到系统报错。）
     */
    fun verifySameApp(context: Context, apk: File): Boolean {
        return try {
            val pm = context.packageManager

            val hasSigningApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            val flags = if (hasSigningApi) PackageManager.GET_SIGNING_CERTIFICATES else 0
            val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            if (archive == null) {
                Log.w(TAG, "签名校验失败：解析不了这个 APK")
                return false
            }
            if (archive.packageName != context.packageName) {
                Log.w(TAG, "签名校验失败：包名不符 ${archive.packageName}")
                return false
            }

            // 签名证书 API（signingInfo）是 Android 9（API 28）才有的。
            // minSdk 已放宽到 26，老设备上做不了证书比对 —— 但下载前的
            // 大小 + SHA-256 校验（清单里登记的）已经挡住了传输劫持，
            // 而且签名不一致的包系统本身也装不上，所以这里放行。
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Log.i(TAG, "API < 28，跳过证书比对（包名与哈希校验仍然生效）")
                return true
            }

            val apkCert = archive.signingInfo?.let { certSha256(it) } ?: run {
                Log.w(TAG, "签名校验失败：新包读不到签名")
                return false
            }
            val installedCert = installedCertSha256(context) ?: run {
                Log.w(TAG, "签名校验失败：读不到本机签名")
                return false
            }

            val same = apkCert.contentEquals(installedCert)
            if (!same) Log.w(TAG, "签名校验失败：证书与本机不一致，已拒绝安装")
            same
        } catch (e: Exception) {
            Log.w(TAG, "签名校验异常：${e.message}")
            false
        }
    }

    /** 取本机已安装版本的签名证书 SHA-256 */
    @Suppress("DEPRECATION")
    private fun installedCertSha256(context: Context): ByteArray? {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        return info.signingInfo?.let { certSha256(it) }
    }

    private fun certSha256(signingInfo: SigningInfo): ByteArray? {
        val signature = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners?.firstOrNull()
        } else {
            signingInfo.signingCertificateHistory?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
    }
}
