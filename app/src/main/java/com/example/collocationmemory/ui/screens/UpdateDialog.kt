package com.example.collocationmemory.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.collocationmemory.data.remote.AppUpdateInfo
import com.example.collocationmemory.ui.viewmodel.UpdateUiState
import com.example.collocationmemory.util.ApkInstaller
import java.util.Locale

/**
 * 「发现新版本 / 正在下载 / 去开启权限 / 开始安装」这一整套弹窗。
 *
 * 设计原则：
 * - 一个状态一种样子，界面不做任何判断，只照着 [state] 渲染；
 * - **强制更新**时不给「以后再说」，点外面也关不掉，必须走完流程；
 * - 下载中可以点「后台下载」先藏起来继续玩 App，下好了会自动再弹出来。
 *
 * 关于「为什么还要点一下系统安装页」：这是 Android 的硬性限制，
 * 第三方 App 不能静默安装（详见 [ApkInstaller] 的说明）。
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onPermissionReturn: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    // 必须无条件注册（不能放在 if 后面），否则从设置页返回时收不到回调
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionReturn()
    }

    if (state is UpdateUiState.Hidden) return

    val forced = state is UpdateUiState.Available && state.forced

    AlertDialog(
        // 强制更新时点外部/返回键都不关闭
        onDismissRequest = { if (!forced) onDismiss() },
        title = { Text(titleOf(state)) },
        text = { DialogBody(state) },
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> TextButton(onClick = onUpdate) { Text("立即更新") }
                is UpdateUiState.Downloading -> TextButton(onClick = onDismiss) { Text("后台下载") }
                is UpdateUiState.NeedPermission -> TextButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        // 个别定制系统没有这个页面，退回通用设置入口
                        runCatching { permissionLauncher.launch(intent) }
                            .onFailure { ApkInstaller.openInstallPermissionSettings(context) }
                    }
                ) { Text("去开启") }
                is UpdateUiState.ReadyToInstall -> TextButton(onClick = onInstall) { Text("开始安装") }
                is UpdateUiState.Failed -> TextButton(onClick = onRetry) { Text("重试") }
                UpdateUiState.Hidden -> Unit
            }
        },
        dismissButton = {
            // 用「空内容」表示没有这个按钮：EmptyComposable 不会产生任何布局节点，
            // 效果等同于 dismissButton = null，但省掉了可空 lambda 的类型麻烦。
            when (state) {
                // 强制更新不给退路
                is UpdateUiState.Available ->
                    if (!state.forced) {
                        TextButton(onClick = onSkip) { Text("以后再说") }
                    }
                is UpdateUiState.NeedPermission -> TextButton(onClick = onDismiss) { Text("稍后") }
                is UpdateUiState.ReadyToInstall -> TextButton(onClick = onDismiss) { Text("稍后") }
                is UpdateUiState.Failed -> TextButton(onClick = onDismiss) { Text("关闭") }
                else -> Unit
            }
        }
    )
}

private fun titleOf(state: UpdateUiState): String = when (state) {
    is UpdateUiState.Available -> "发现新版本 v${state.info.versionName}"
    is UpdateUiState.Downloading -> "正在下载更新包"
    is UpdateUiState.NeedPermission -> "还需要一步"
    is UpdateUiState.ReadyToInstall -> "更新包已就绪"
    is UpdateUiState.Failed -> "更新失败"
    UpdateUiState.Hidden -> ""
}

@Composable
private fun DialogBody(state: UpdateUiState) {
    when (state) {
        is UpdateUiState.Available -> AvailableBody(state.info, state.forced)
        is UpdateUiState.Downloading -> DownloadingBody(state)
        is UpdateUiState.NeedPermission -> NeedPermissionBody()
        is UpdateUiState.ReadyToInstall -> ReadyBody()
        is UpdateUiState.Failed -> Text(state.message)
        UpdateUiState.Hidden -> Unit
    }
}

@Composable
private fun AvailableBody(info: AppUpdateInfo, forced: Boolean) {
    Column {
        if (info.changelog.isNotBlank()) {
            Text(
                text = "更新内容",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            // 更新说明可能很长，限高 + 可滚动，别把弹窗撑出屏幕
            Text(
                text = info.changelog.trim(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(12.dp))
        }

        if (info.apkSize > 0) {
            Text(
                text = "安装包大小：${formatSize(info.apkSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
        }

        if (forced) {
            Text(
                text = "本次为必要更新，完成前无法继续使用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "更新不会影响你的学习进度、生词本和打卡记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DownloadingBody(state: UpdateUiState.Downloading) {
    Column {
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 服务器没给 Content-Length 时用不确定进度条，避免显示 0%
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.total > 0) "${(state.progress * 100).toInt()}%" else "下载中…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.total > 0) {
                    "${formatSize(state.downloaded)} / ${formatSize(state.total)}"
                } else {
                    formatSize(state.downloaded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "可以点「后台下载」先去学习，下载完成会自动提示安装。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NeedPermissionBody() {
    Column {
        Text(
            text = "Android 需要你允许「搭配记忆」安装应用，才能完成这次更新。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "1. 点下面的「去开启」\n" +
                "2. 在打开的页面里找到「搭配记忆」\n" +
                "3. 打开「允许安装未知应用」开关\n" +
                "4. 返回本应用，下载会自动继续",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "这是 Android 的安全限制，任何应用都无法跳过这一步。只为这一次更新授权即可，装完可以随时关掉。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ReadyBody() {
    Column {
        Text(
            text = "更新包已下载并校验通过。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "点「开始安装」后，系统会弹出安装界面，按提示点一下「安装」就完成了。\n\n" +
                "安装是覆盖升级，你的学习进度、生词本、打卡记录都会原样保留。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 把字节数变成「20.5 MB」这种好读的形式 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "未知"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.CHINA, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.CHINA, "%.1f MB", mb)
}
