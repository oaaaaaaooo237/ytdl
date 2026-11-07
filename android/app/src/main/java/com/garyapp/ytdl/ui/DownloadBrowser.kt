package com.garyapp.ytdl.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.garyapp.ytdl.core.policy.UrlPolicy

@Composable
internal fun DownloadBrowser(initialUrl: String, onClose: () -> Unit, onSelect: (String) -> Unit) {
    var address by remember { mutableStateOf(initialUrl) }
    var page by remember { mutableStateOf("") }
    var firstPage by remember { mutableStateOf(UrlPolicy.evaluate(initialUrl).rawUrlForExecution) }
    var view by remember { mutableStateOf<WebView?>(null) }
    var showMedia by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf(false) }
    var heldLink by remember { mutableStateOf<String?>(null) }
    val media = remember { mutableStateListOf<String>() }
    val currentOnSelect = rememberUpdatedState(onSelect)
    val downloadLabel = appText("下载", "Download")

    val focusManager = LocalFocusManager.current
    fun openAddress() {
        val url = UrlPolicy.evaluate(address).rawUrlForExecution
        pageError = url == null
        if (url == null) return
        focusManager.clearFocus()
        if (view == null) firstPage = url else view?.loadUrl(url)
    }

    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = pageError,
                    label = { Text(appText("网页地址", "Page address")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openAddress() }),
                )
                TextButton(onClick = ::openAddress) { Text(appText("打开", "Open")) }
                TextButton(onClick = onClose) { Text(appText("关闭", "Close")) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                SettingsActionButton(enabled = page.isNotEmpty() && !pageError, onClick = { onSelect(page) }) {
                    Text(appText("分析当前网页", "Analyze page"))
                }
                SettingsActionButton(onClick = { showMedia = true }) {
                    Text(appText("查看检测到的媒体", "Detected media") + " (${media.size})")
                }
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(enabled = canGoBack, onClick = { view?.goBack() }) { Text(appText("后退", "Back")) }
                TextButton(enabled = canGoForward, onClick = { view?.goForward() }) { Text(appText("前进", "Forward")) }
                TextButton(enabled = page.isNotEmpty(), onClick = { view?.reload() }) { Text(appText("刷新", "Reload")) }
            }
            if (pageError) {
                Text(
                    appText("无法打开网页，请检查地址或网络。", "Could not open the page. Check the address or connection."),
                    Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (firstPage == null) {
                Text(
                    appText("输入网页地址开始浏览。", "Enter a page address to start browsing."),
                    Modifier.padding(16.dp),
                )
            } else AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        setOnLongClickListener { web ->
                            val hit = (web as WebView).hitTestResult
                            val link = if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                            ) UrlPolicy.evaluate(hit.extra.orEmpty()).rawUrlForExecution else null
                            heldLink = link
                            link != null
                        }
                        setDownloadListener { url, _, _, _, _ ->
                            UrlPolicy.evaluate(url).rawUrlForExecution?.let(currentOnSelect.value)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                                !UrlPolicy.evaluate(request.url.toString()).isAllowed

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                pageError = false
                                address = url
                                page = UrlPolicy.evaluate(url).rawUrlForExecution.orEmpty()
                                media.clear()
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: android.webkit.WebResourceError,
                            ) {
                                if (request.isForMainFrame) pageError = true
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                if (request.isForMainFrame) pageError = true
                            }

                            override fun onRenderProcessGone(
                                webView: WebView,
                                detail: android.webkit.RenderProcessGoneDetail,
                            ): Boolean {
                                if (view === webView) {
                                    view = null
                                    firstPage = null
                                    page = ""
                                    canGoBack = false
                                    canGoForward = false
                                    media.clear()
                                    pageError = true
                                }
                                return true
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                val extension = request.url.lastPathSegment?.substringAfterLast('.')?.lowercase()
                                if (request.method == "GET" && request.url.userInfo == null &&
                                    extension in browserMediaExtensions && UrlPolicy.evaluate(url).isAllowed
                                ) {
                                    view.post { if (media.size < 40 && url !in media) media.add(url) }
                                }
                                return null
                            }
                        }
                        view = this
                        firstPage?.let(::loadUrl)
                    }
                },
                onRelease = { released ->
                    if (view === released) released.stopLoading()
                    released.destroy()
                },
            )
        }
    }
    heldLink?.let { link ->
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { heldLink = null },
            title = { Text(appText("链接操作", "Link actions")) },
            text = { Text(link, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                SettingsActionButton(onClick = {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText(downloadLabel, link))
                    heldLink = null
                    currentOnSelect.value(link)
                }) { Text(downloadLabel) }
            },
            dismissButton = {
                TextButton(onClick = { heldLink = null }) { Text(appText("取消", "Cancel")) }
            },
        )
    }
    if (showMedia) {
        AlertDialog(
            onDismissRequest = { showMedia = false },
            title = { Text(appText("检测到的媒体", "Detected media")) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    if (media.isEmpty()) item { Text(appText("播放网页中的媒体后再查看。", "Play media on the page and check again.")) }
                    items(media.size) { index ->
                        TextButton(onClick = { onSelect(media[index]) }) {
                            Text(media[index], maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMedia = false }) { Text(appText("关闭", "Close")) }
            },
        )
    }
}

private val browserMediaExtensions = setOf("mp4", "webm", "m3u8", "mpd")
