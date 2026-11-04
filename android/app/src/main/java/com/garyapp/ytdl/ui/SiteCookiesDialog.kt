package com.garyapp.ytdl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.CookieSites
import com.garyapp.ytdl.core.settings.SiteCookies

@Composable
internal fun SiteCookiesDialog(
    settings: AppSettings,
    onChoose: (String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onCheck: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val sites = linkedMapOf(
        "x.com" to SiteCookies("X", "x.com", null),
        "youtube.com" to SiteCookies("YouTube", "youtube.com", null),
    ) + settings.siteCookies
    AlertDialog(
        modifier = Modifier.testTag("site-cookies-dialog"),
        onDismissRequest = onDismiss,
        title = { Text("网站登录状态") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("能直接下载的网站无需设置。配置后仅对应网站使用；仅保存文件引用，App 内登录状态加密保存。")
                OutlinedTextField(name, { name = it }, label = { Text("网站名称") },
                    singleLine = true, modifier = Modifier.testTag("site-cookie-name"))
                OutlinedTextField(domain, { domain = it }, label = { Text("域名，例如 x.com") },
                    singleLine = true, modifier = Modifier.testTag("site-cookie-domain"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = {
                    val host = CookieSites.normalizeDomain(domain)
                    if (host == null) error = "请输入域名，不含 https:// 和路径。"
                    else { onAdd(name.ifBlank { host }, host); name = ""; domain = ""; error = null }
                }, modifier = Modifier.testTag("site-cookie-add")) { Text("添加网站") }
                sites.values.forEach { site ->
                    HorizontalDivider()
                    Text("${site.name} · ${site.domain}", style = MaterialTheme.typography.titleSmall)
                    val status = site.reference?.displayName?.takeIf { it.isNotBlank() }
                        ?: if (site.domain !in settings.siteCookies && settings.cookiesReference != null)
                            "未单独配置 · 沿用旧版通用文件" else "未配置 · 匿名访问"
                    Text(status,
                        modifier = Modifier.testTag("site-cookie-status-${site.domain}"))
                    Row {
                        TextButton(onClick = { onChoose(site.name, site.domain) },
                            modifier = Modifier.testTag("site-cookie-file-${site.domain}")) { Text("选择文件") }
                        TextButton(onClick = { onLogin(site.name, site.domain) },
                            modifier = Modifier.testTag("site-cookie-login-${site.domain}")) { Text("登录") }
                    }
                    Row {
                        TextButton(onClick = { onCheck(site.domain) }, enabled = site.reference != null,
                            modifier = Modifier.testTag("site-cookie-check-${site.domain}")) { Text("检查可读性") }
                        TextButton(onClick = { onClear(site.domain) }, enabled = site.reference != null,
                            modifier = Modifier.testTag("site-cookie-clear-${site.domain}")) { Text("清除") }
                    }
                }
                settings.cookiesReference?.let {
                    HorizontalDivider()
                    Text("旧版通用 · 未匹配网站时使用")
                    Text(it.displayName ?: "cookies 文件")
                    Row {
                        TextButton(onClick = { onCheck("") }) { Text("检查可读性") }
                        TextButton(onClick = { onClear("") }) { Text("清除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
