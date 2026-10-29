package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.ui.theme.AppColors
import com.example.collocationmemory.ui.viewmodel.MainViewModel

/**
 * 词库预览：默认摊开当前词本的全部词条；输入搜索词后改为**跨所有词本**搜索。
 *
 * 入口在「设置 → 当前单词本」卡片。用途是「没学先预览」——
 * 学之前翻一遍心里有数，也能按关键词搜（搜的时候是全词库范围，
 * 结果卡上标注每个词条所属的词本）。
 * 已学过的词打「已学」标记，未学的不标记，一眼分清进度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPreviewScreen(viewModel: MainViewModel, navController: NavController) {
    val currentBookItems by viewModel.bookPreviewItems.collectAsState()
    val allBooksItems by viewModel.allBooksPreviewItems.collectAsState()
    val bookName by viewModel.homeStats.collectAsState()
    var query by remember { mutableStateOf("") }

    // 搜索词为空 → 当前词本全量；非空 → 全词库过滤（跨词本搜索）
    val searching = query.isNotBlank()
    val filtered = remember(currentBookItems, allBooksItems, query) {
        if (!searching) currentBookItems
        else allBooksItems.filter {
            it.english.contains(query, ignoreCase = true) ||
                it.chinese.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词库预览") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { BottomNavBar(navController = navController, currentRoute = Screen.Settings.route) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 标题行：不搜索时显示当前词本规模；搜索时显示全词库匹配数
            Text(
                text = if (searching) {
                    "全部词本 · 匹配 ${filtered.size} 条"
                } else {
                    "${bookName.bookName} · 共 ${currentBookItems.size} 条 · 已学 ${currentBookItems.count { it.isLearned }} 条"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索全部词本的英文或中文") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!searching) "这个词本还是空的" else "没有匹配「$query」的词条",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { collocation ->
                        BookPreviewItem(collocation, showBookName = searching)
                    }
                    item { BottomBarHeightSpacer() }
                }
            }
        }
    }
}

@Composable
fun BookPreviewItem(collocation: Collocation, showBookName: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = collocation.english,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (collocation.isLearned) {
                    Text(
                        text = "已学",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.successColor()
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = collocation.chinese,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            // 跨词本搜索时标注所属词本，让用户知道这个词在哪个本里
            if (showBookName && collocation.bookName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = collocation.bookName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}
