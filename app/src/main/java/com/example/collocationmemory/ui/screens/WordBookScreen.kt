package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.collocationmemory.Screen
import com.example.collocationmemory.data.local.Collocation
import com.example.collocationmemory.ui.theme.AppColors
import com.example.collocationmemory.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBookScreen(viewModel: MainViewModel, navController: NavController) {
    val wordBook by viewModel.getWordBookFlow().collectAsState(initial = emptyList())
    val learnedElsewhere by viewModel.learnedElsewhere.collectAsState()
    val notebookCount by viewModel.notebookEntries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生词本") },
                // 「笔记本」入口：原来是导航栏图标上挂 11dp 小三角，
                // 和生词本本身的点击区叠在一起，极易误触；
                // 现在挪到生词本页顶栏，点击区充足、位置也直观。
                actions = {
                    Row(
                        modifier = Modifier
                            .clickable { navController.navigate(Screen.Notebook.route) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "笔记本",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (notebookCount.isNotEmpty()) "笔记本 ${notebookCount.size}" else "笔记本",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { BottomNavBar(navController = navController, currentRoute = Screen.WordBook.route) }
    ) { paddingValues ->
        if (wordBook.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "生词本为空\n学习时答错的搭配会自动加入",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                items(wordBook, key = { it.id }) { collocation ->
                    WordBookItem(
                        collocation = collocation,
                        // 这个词在别的词本里已经背会了 → 打「熟」标记
                        learnedInOtherBook = collocation.english in learnedElsewhere &&
                            !collocation.isLearned,
                        onRemove = { viewModel.toggleWordBook(collocation) }
                    )
                }
                item { BottomBarHeightSpacer() }
            }
        }
    }
}

@Composable
fun WordBookItem(
    collocation: Collocation,
    learnedInOtherBook: Boolean = false,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            RowCentered {
                Text(
                    text = collocation.english,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (learnedInOtherBook) {
                    // 同一个词在别的词本里已经背会了：这里只做提示，不再让你重复背
                    Text(
                        text = "熟 · 已在其他词本学会",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.successColor()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "移除生词本",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = collocation.chinese,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            if (collocation.example.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = collocation.example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (collocation.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = collocation.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RowCentered(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}
