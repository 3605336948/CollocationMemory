package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.collocationmemory.legal.CopyrightNotice
import com.example.collocationmemory.ui.theme.AppColors

/**
 * 启动时的「版权与使用声明」弹窗。
 *
 * 设计取舍：
 * - 不用 AlertDialog，改用自绘 Dialog + 卡片，这样圆角/配色能和 App 其它卡片一致；
 * - 正文可滚动并设高度上限，避免小屏上按钮被挤出屏幕；
 * - 「完全免费软件」用语义红色强调（颜色随主题切换，并已纳入对比度自检）；
 * - 两个按钮：主按钮=同意（每次启动仍会看到），次按钮=同意且不再提示（写偏好后不再弹）。
 */
@Composable
fun CopyrightNoticeDialog(
    onAgree: () -> Unit,
    onAgreeAndDontShowAgain: () -> Unit
) {
    // 返回键 / 点击外部都不关闭：必须先明确表态
    Dialog(
        onDismissRequest = { /* 故意留空，强制用户选择 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = CopyrightNotice.TITLE,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请阅读以下条款后再开始使用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CopyrightNotice.PARAGRAPHS.forEach { paragraph ->
                        Text(
                            text = highlightFreePhrase(paragraph),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.6
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 两种权利分开显示，避免混淆：
                // 软件程序是作者自己的，学习资料的版权属于资料提供方（授权内置）。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        AttributionRow(
                            label = "软件作者",
                            value = CopyrightNotice.SOFTWARE_AUTHOR,
                            emphasize = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AttributionRow(
                            label = "资料版权",
                            value = "${CopyrightNotice.MATERIALS_SOURCE}（授权内置）",
                            emphasize = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onAgree,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "我同意",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                TextButton(
                    onClick = onAgreeAndDontShowAgain,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "同意，下次不再提示",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

/** 一行「标签 + 值」的署名信息 */
@Composable
private fun AttributionRow(label: String, value: String, emphasize: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            // 固定宽度：让上下两行的值左对齐（"软件作者" 3 字 vs "资料来源" 4 字）
            modifier = Modifier.width(58.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            },
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** 把「完全免费软件」标红 —— 这是整个声明里最重要的一句。 */
@Composable
private fun highlightFreePhrase(text: String): AnnotatedString {
    val phrase = CopyrightNotice.RED_PHRASE
    val index = text.indexOf(phrase)
    if (index < 0) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(
            SpanStyle(
                color = AppColors.errorColor(),
                fontWeight = FontWeight.Bold
            )
        ) {
            append(phrase)
        }
        append(text.substring(index + phrase.length))
    }
}
