package com.garyapp.ytdl.cookies

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.garyapp.ytdl.core.settings.CookieSites

/** User-operated HTTPS login. Only the configured site's cookies can be saved. */
class SiteLoginActivity : Activity() {
    private var browser: WebView? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val domain = CookieSites.normalizeDomain(intent.getStringExtra("domain").orEmpty())
        if (domain == null) { finish(); return }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
        }
        setContentView(layout)
        layout.addView(TextView(this).apply {
            text = "登录 $domain\n请使用网站自己的账号完成登录，再保存。Google 等联合登录不支持此内置页面，请在外部浏览器登录后导入 cookies 文件。登录状态仅用于该网站下载。"
        })
        val address = TextView(this)
        layout.addView(address)
        val actions = LinearLayout(this)
        layout.addView(actions)
        val save = Button(this).apply { text = "保存登录状态"; isEnabled = false }
        actions.addView(save)
        actions.addView(Button(this).apply { text = "取消"; setOnClickListener { finish() } })
        val cookies = CookieManager.getInstance()
        val web = WebView(this).also { browser = it }
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme != "https"
            override fun onPageFinished(view: WebView, url: String) {
                address.text = android.net.Uri.parse(url).let { "${it.scheme}://${it.host.orEmpty()}" }
                save.isEnabled = !saving
            }
        }
        // Each login starts clean; saved website profiles live in the encrypted store.
        cookies.removeAllCookies {
            if (!isFinishing) web.loadUrl("https://$domain")
        }
        save.setOnClickListener {
            if (saving) return@setOnClickListener
            val header = cookies.getCookie("https://$domain").orEmpty()
            if (!SiteLoginStore.loginCookiesReady(domain, header)) {
                Toast.makeText(this, "网站登录尚未完成，请完成账号、密码和必要验证，看到账号页面后再保存。", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            saving = true
            save.isEnabled = false
            Thread {
                val result = runCatching { SiteLoginStore.fromContext(this).save(domain, header) }
                runOnUiThread {
                    result.fold(onSuccess = { reference ->
                        setResult(RESULT_OK, Intent().putExtra("domain", domain).putExtra("reference", reference.value))
                        cookies.removeAllCookies { finish() }
                    }, onFailure = {
                        saving = false
                        save.isEnabled = true
                        Toast.makeText(this, "登录状态保存失败，请重试。", Toast.LENGTH_LONG).show()
                    })
                }
            }.start()
        }
    }

    override fun onDestroy() {
        browser?.apply { stopLoading(); clearHistory(); clearCache(true); destroy() }
        browser = null
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        super.onDestroy()
    }
}
