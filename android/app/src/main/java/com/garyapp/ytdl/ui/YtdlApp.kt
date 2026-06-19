package com.garyapp.ytdl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Immutable
data class YtdlDestination(
    val route: String,
    val label: String,
    val title: String,
    val summary: String,
)

fun ytdlNavigationDestinations(): List<YtdlDestination> = listOf(
    YtdlDestination(
        route = "download",
        label = "下载",
        title = "下载",
        summary = "粘贴公开视频页面地址，分析后再开始保存。",
    ),
    YtdlDestination(
        route = "formats",
        label = "格式",
        title = "格式",
        summary = "选择分辨率、音频、容器和字幕处理方式。",
    ),
    YtdlDestination(
        route = "queue",
        label = "队列",
        title = "队列",
        summary = "查看等待、进行中、暂停、失败和完成任务。",
    ),
    YtdlDestination(
        route = "history",
        label = "历史",
        title = "历史",
        summary = "查看已保存记录，后续可打开、分享或重新下载。",
    ),
    YtdlDestination(
        route = "settings",
        label = "设置",
        title = "设置",
        summary = "管理保存位置、cookies 引用、解析器版本和隐私说明。",
    ),
)

@Composable
fun YtdlApp() {
    val destinations = ytdlNavigationDestinations()
    var selectedRoute by rememberSaveable { mutableStateOf(destinations.first().route) }
    val selected = destinations.firstOrNull { it.route == selectedRoute } ?: destinations.first()

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.route == selected.route,
                        onClick = { selectedRoute = destination.route },
                        icon = {
                            Text(
                                text = destination.label.first().toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        YtdlPage(
            destination = selected,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun YtdlPage(
    destination: YtdlDestination,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = destination.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = destination.label.first().toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${destination.label}页",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Android Play MVP Task 1 壳层",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
