package com.garyapp.ytdl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.garyapp.ytdl.ui.theme.LocalYtdlAppPalette
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.CookieSites
import com.garyapp.ytdl.core.settings.SiteCookies

@Composable
internal fun SiteCookiesSettings(
    settings: AppSettings,
    onChoose: (String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onCheck: (String) -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    val invalidDomain = appText("请输入域名，不含 https:// 和路径。", "Enter a domain without https:// or a path.")
    var error by remember { mutableStateOf<String?>(null) }
    val sites = linkedMapOf(
        "x.com" to SiteCookies("X", "x.com", null),
        "youtube.com" to SiteCookies("YouTube", "youtube.com", null),
    ) + settings.siteCookies
    var importSite by remember { mutableStateOf<SiteCookies?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sites.values.forEach { site ->
            CookiesSiteCard {
                Text("${site.name} · ${site.domain}", style = MaterialTheme.typography.titleSmall)
                val status = site.reference?.displayName?.takeIf { it.isNotBlank() }
                    ?: if (site.domain !in settings.siteCookies && settings.cookiesReference != null)
                        appText("未单独配置 · 沿用旧版通用文件", "Uses the legacy file") else appText("未配置 · 匿名访问", "No cookies · Anonymous access")
                Text(status, modifier = Modifier.testTag("site-cookie-status-${site.domain}"),
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionButton(onClick = { importSite = site },
                        modifier = Modifier.testTag("site-cookie-file-${site.domain}")) { Text(appText("导入文件", "Import file")) }
                    SettingsActionButton(onClick = { onLogin(site.name, site.domain) },
                        modifier = Modifier.testTag("site-cookie-login-${site.domain}")) { Text(appText("登录", "Sign in")) }
                }
                if (site.reference != null) {
                    Row {
                        SettingsActionButton(onClick = { onCheck(site.domain) },
                            modifier = Modifier.testTag("site-cookie-check-${site.domain}")) { Text(appText("检查可读性", "Check file")) }
                        SettingsActionButton(onClick = { onClear(site.domain) },
                            modifier = Modifier.testTag("site-cookie-clear-${site.domain}")) { Text(appText("清除", "Clear")) }
                    }
                }
            }
        }
        settings.cookiesReference?.let { reference ->
            CookiesSiteCard {
                Text(appText("旧版通用 · 未匹配网站时使用", "Legacy file · Used for unmatched sites"))
                Text(reference.displayName ?: "Cookies 文件")
                Row {
                    SettingsActionButton(onClick = { onCheck("") }) { Text(appText("检查可读性", "Check file")) }
                    SettingsActionButton(onClick = { onClear("") }) { Text(appText("清除", "Clear")) }
                }
            }
        }
        CookiesSiteCard {
            Text(appText("自定义网站", "Custom site"), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(name, { name = it }, label = { Text(appText("网站名称", "Site name")) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("site-cookie-name"))
            OutlinedTextField(domain, { domain = it }, label = { Text(appText("域名，例如 x.com", "Domain, e.g. x.com")) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("site-cookie-domain"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SettingsActionButton(onClick = {
                val host = CookieSites.normalizeDomain(domain)
                if (host == null) error = invalidDomain
                else { onAdd(name.ifBlank { host }, host); name = ""; domain = ""; error = null }
            }, modifier = Modifier.testTag("site-cookie-add")) { Text(appText("添加网站", "Add site")) }
        }
    }

    importSite?.let { site ->
        val isYouTube = site.domain == "youtube.com"
        val signInStep = if (isYouTube) {
            appText("在手机 Firefox 的隐私窗口登录 YouTube，再在同一个标签页打开 youtube.com/robots.txt。", "Sign in to YouTube in a private Firefox tab, then open youtube.com/robots.txt in the same tab.")
        } else {
            appText("在手机 Firefox 打开 ${site.domain} 并登录，停留在该网站。", "Open ${site.domain} in Firefox for Android, sign in, and stay on the site.")
        }
        AlertDialog(
            onDismissRequest = { importSite = null },
            title = { Text(appText("导入 ${site.name} 的 Cookies", "Import cookies for ${site.name}")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(appText("手机上获取 Cookies", "Get cookies on your phone"), style = MaterialTheme.typography.titleSmall)
                    Text(appText("Chrome 手机版无法安装此类导出扩展。可在手机 Firefox 重新登录同一网站后导出。其他浏览器的扩展支持因版本而异，可使用下列 Firefox 路径。", "Chrome for Android does not support these export extensions. Sign in to the same site in Firefox for Android to export its cookies."))
                    Text(appText("1. 手机 Firefox → 菜单 → 扩展，搜索并安装 cookies.txt（作者 Lennon Hill）。", "1. In Firefox for Android, open Menu → Extensions and install cookies.txt by Lennon Hill."))
                    Text("2. $signInStep")
                    Text(appText("3. 保持在该网站，打开 Firefox 菜单 → 扩展 → cookies.txt，导出当前站点。文件保存到手机的 Download（下载）目录。", "3. Stay on the site. Open Menu → Extensions → cookies.txt and export the current site to Downloads."))
                    if (isYouTube) Text(appText("导出后关闭无痕窗口。", "Close the private window after exporting."))
                    Text(appText("从电脑传到手机", "Transfer from a computer"), style = MaterialTheme.typography.titleSmall)
                    Text(appText("1. 在电脑 Firefox 登录该网站。通过菜单 → 扩展和主题安装 cookies.txt（作者 Lennon Hill），在该网站标签页用扩展导出 Netscape 格式的 .txt 文件。", "1. Sign in to the site in desktop Firefox. Install cookies.txt by Lennon Hill from Add-ons and themes, then export a Netscape-format .txt file from the site tab."))
                    Text(appText("2. 用 USB 连接手机，在手机上选择「文件传输」。在电脑文件管理器打开手机内部存储，把导出的 .txt 复制到 Download（下载）目录。", "2. Connect the phone by USB and choose File transfer on the phone. Open its internal storage on your computer and copy the .txt file to Download."))
                    Text(appText("手机已有文件", "Use a file already on your phone"), style = MaterialTheme.typography.titleSmall)
                    Text(appText("点「选择文件」，先到 Download（下载）或 Documents（文档）找已有的 cookies.txt，也可切到当时保存的目录。已有对应网站的 Netscape 格式文件时，可直接选择。", "Tap Choose file and look in Downloads, Documents, or the folder where you saved it. Select the existing Netscape-format cookies.txt for this site."))
                    Text(appText("手机浏览器的登录数据保存在浏览器私有目录。若手机上没有现成的 .txt，可用上面的导出方法，或返回网站卡片点「登录」。", "Browser sign-in data is stored privately by the browser. If you have no .txt file, follow an export method above or tap Sign in on the site card."))
                }
            },
            confirmButton = {
                Button(onClick = { importSite = null; onChoose(site.name, site.domain) },
                    shape = RectangleShape, modifier = Modifier.testTag("site-cookie-import-confirm")) {
                    Text(appText("选择文件", "Choose file"))
                }
            },
            dismissButton = { SettingsActionButton(onClick = { importSite = null }) { Text(appText("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun CookiesSiteCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalYtdlAppPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = palette.cardBackground,
        contentColor = palette.titleText,
        border = BorderStroke(1.dp, palette.borderColor),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}
