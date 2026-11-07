package com.garyapp.ytdl.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun FirstRunPermissionGuide(
    onChooseFolder: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val packageUri = Uri.fromParts("package", context.packageName, null)
    fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(appDetails())
        }
    }

    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("ytdl-first-run-guide"),
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(appText("权限与保存设置", "Permissions and storage")) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(appText("申请通知权限", "Allow notifications"), style = MaterialTheme.typography.titleSmall)
                Text(appText("用于在通知栏显示下载进度、完成和失败提醒。关闭时仍可下载，请在任务页查看进度和结果。", "See download progress and completion or failure alerts in notifications. Downloads still work with notifications off. Check their progress in Tasks."))
                SettingsActionButton(
                    modifier = Modifier.testTag("ytdl-first-run-notifications"),
                    onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            appDetails()
                        }
                        openSettings(intent)
                    },
                ) { Text(appText("去设置", "Open settings")) }

                Text(appText("允许后台数据传输", "Allow background data"), style = MaterialTheme.typography.titleSmall)
                Text(appText("用于切到后台时继续联网下载。若系统限制后台数据，下载在省流量模式下可能暂停。", "Keep downloads connected in the background. Data Saver can pause downloads when background data is restricted."))
                SettingsActionButton(
                    modifier = Modifier.testTag("ytdl-first-run-background-data"),
                    onClick = {
                        openSettings(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageUri))
                    },
                ) { Text(appText("去设置", "Open settings")) }

                Text(appText("后台用电设为不受限制", "Allow unrestricted battery use"), style = MaterialTheme.typography.titleSmall)
                Text(appText("可减少锁屏后被省电策略暂停的情况。请在系统列表中找到 CaptainFlash 并调整；保留限制时，部分手机可能暂停后台任务。", "Find CaptainFlash in system settings to reduce pauses when the screen locks. Some phones may pause background tasks if battery restrictions remain enabled."))
                SettingsActionButton(
                    modifier = Modifier.testTag("ytdl-first-run-battery"),
                    onClick = { openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                ) { Text(appText("去设置", "Open settings")) }

                Text(appText("设置默认保存位置", "Set the default save location"), style = MaterialTheme.typography.titleSmall)
                Text(appText("选定文件夹后，可在文件管理器中找到成品。暂不选择可在首次下载时再选。", "Choose a folder to find finished downloads in your file manager. You can also choose one when you start your first download."))
                SettingsActionButton(
                    onClick = onChooseFolder,
                    modifier = Modifier.testTag("ytdl-first-run-folder"),
                ) { Text(appText("选择保存文件夹", "Choose save folder")) }
            }
        },
        confirmButton = {
            Button(
                onClick = onFinish,
                shape = RectangleShape,
                modifier = Modifier.testTag("ytdl-first-run-continue"),
            ) { Text(appText("开始使用", "Get started")) }
        },
    )
}
