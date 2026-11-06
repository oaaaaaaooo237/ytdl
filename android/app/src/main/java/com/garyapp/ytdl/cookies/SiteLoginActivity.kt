package com.garyapp.ytdl.cookies

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.ui.theme.YtdlThemeMode
import com.garyapp.ytdl.ui.theme.themeConfigForSettings
import com.garyapp.ytdl.ui.theme.ytdlAppPaletteForPreset

/** User-operated HTTPS login. Only the configured site's cookies can be saved. */
class SiteLoginActivity : Activity() {
    private var browser: WebView? = null
    private var saving = false
    private val language by lazy {
        SettingsRepository.fromContext(this).getSettings().language.resolve(resources.configuration.locales[0])
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val domain = CookieSites.normalizeDomain(intent.getStringExtra("domain").orEmpty())
        if (domain == null) { finish(); return }
        val config = themeConfigForSettings(SettingsRepository.fromContext(this).getSettings())
        val dark = when (config.mode) {
            YtdlThemeMode.Light -> false
            YtdlThemeMode.Dark -> true
            YtdlThemeMode.System ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val palette = ytdlAppPaletteForPreset(config.colorPreset.id, dark)
        val inset = (16 * resources.displayMetrics.density).toInt()
        val space = inset / 2
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.appBackground.toArgb())
        }
        setContentView(layout)
        WindowCompat.getInsetsController(window, layout).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left + inset, bars.top + space, bars.right + inset, bars.bottom + space)
            insets
        }
        layout.addView(TextView(this).apply {
            text = language.pick("登录 $domain", "Sign in to $domain")
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.titleText.toArgb())
        })
        layout.addView(TextView(this).apply {
            text = language.pick("登录后点保存。联合登录请在浏览器完成，再导入 Cookies 文件。", "Tap Save after signing in. For third-party sign-in, use a browser and import its cookies file.")
            setTextColor(palette.softText.toArgb())
        })
        val address = TextView(this).apply { setTextColor(palette.softText.toArgb()) }
        layout.addView(address)
        val actions = LinearLayout(this).apply { setPadding(0, space, 0, space) }
        layout.addView(actions)
        fun action(label: String, background: Int, foreground: Int) = Button(this).apply {
            text = label
            minHeight = inset * 3
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(palette.softText.toArgb(), foreground),
            ))
            this.background = RippleDrawable(
                ColorStateList.valueOf(palette.borderColor.toArgb()), ColorDrawable(background), null,
            )
        }
        val save = action(language.pick("保存登录状态", "Save sign-in"), palette.downloadAccent.toArgb(), palette.primaryActionText.toArgb())
            .apply { isEnabled = false }
        actions.addView(save, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = space })
        actions.addView(action(language.pick("取消", "Cancel"), palette.segmentedBackground.toArgb(), palette.titleText.toArgb())
            .apply { setOnClickListener { finish() } }, LinearLayout.LayoutParams(0, -2, 1f))
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
                Toast.makeText(this, language.pick("网站登录尚未完成，请完成账号、密码和必要验证，看到账号页面后再保存。", "Finish signing in and any verification. Save after your account page appears."), Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this, language.pick("登录状态保存失败，请重试。", "Could not save sign-in. Try again."), Toast.LENGTH_LONG).show()
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
