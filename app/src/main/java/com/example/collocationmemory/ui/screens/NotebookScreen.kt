package com.example.collocationmemory.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.collocationmemory.data.local.NotebookEntry
import com.example.collocationmemory.ui.viewmodel.MainViewModel

/**
 * 笔记本：用户自己的小本子。
 *
 * 内容来自学习/复习时点卡片上的「记」，用户在这里随便改写 [NotebookEntry.note]。
 * 数据只存本机（DataStore），不参与同步。
 *
 * 交互：点开条目直接改备注（每行一个输入框），改动后出现「保存」；
 * 右上角垃圾桶删除。样式沿用生词本页的卡片语言。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(viewModel: MainViewModel, navController: NavController) {
    val entries by viewModel.notebookEntries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("笔记本") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "笔记本还是空的\n学习或复习时点卡片右上角的「记」收进来",
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    NotebookItem(
                        entry = entry,
                        onUpdateNote = { viewModel.updateNotebookNote(entry.id, it) },
                        onRemove = { viewModel.removeNotebookEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookItem(
    entry: NotebookEntry,
    onUpdateNote: (String) -> Unit,
    onRemove: () -> Unit
) {
    // 本地草稿 + 是否已改动：DataStore 每键都写盘，不能敲一个字存一次
    var draft by remember(entry.id, entry.note) { mutableStateOf(entry.note) }
    val dirty = draft != entry.note

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.english,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.chinese,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "从笔记本删除",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("写点什么…（例句、辨析、自己的话都行）") },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp)
            )
            if (dirty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onUpdateNote(draft) }) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
