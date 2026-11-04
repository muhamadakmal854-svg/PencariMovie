package com.mts.pencarimovie

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val PREF_PM_SERVER_URL = "pm_server_url"
const val PREF_PM_TG_COOKIES = "pm_tg_cookies"
const val DEFAULT_PM_SERVER_URL = "http://127.0.0.1:8088"
const val PM_MAIN_URL = "https://flixtorbot.pencarimovie.com"
const val PM_LOGIN_URL = "https://flixtorbot.pencarimovie.com/wp-login.php"

class PencariMovieSettingsDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentContainer = FragmentContainerView(requireContext())
        fragmentContainer.id = View.generateViewId()
        fragmentContainer.layoutParams = ViewGroup.LayoutParams(-1, -1)
        return fragmentContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction()
            .replace(view.id, PrefsFragment())
            .commit()
    }

    class PrefsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val ctx = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(ctx)
            preferenceScreen = screen

            // Kategori 1: Pelayan Penstriman Terbina Dalam (Port 8088)
            val serverCategory = PreferenceCategory(ctx).apply {
                title = "Pelayan Penstriman Terbina Dalam (Port 8088)"
            }
            screen.addPreference(serverCategory)

            val serverStatusPref = Preference(ctx).apply {
                title = "Status Enjin Rust Tempatan"
                val isRunning = PencariMovieServerManager.isRunning()
                summary = if (isRunning) "✅ Aktif & Bersedia di http://127.0.0.1:8088" else "⚠️ Belum Berjalan (Klik 'Mulakan Pelayan' di bawah)"
            }
            serverCategory.addPreference(serverStatusPref)

            val restartServerPref = Preference(ctx).apply {
                title = "Mulakan / Mulakan Semula Pelayan"
                summary = "Mulakan enjin Rust tempatan secara automatik di port 8088"
                setOnPreferenceClickListener {
                    Toast.makeText(ctx, "Sedang memulakan enjin Rust PencariMovie...", Toast.LENGTH_SHORT).show()
                    PencariMovieServerManager.startServerAsync(ctx) { success ->
                        Handler(Looper.getMainLooper()).post {
                            if (success) {
                                serverStatusPref.summary = "✅ Aktif & Bersedia di http://127.0.0.1:8088"
                                Toast.makeText(ctx, "✅ Pelayan berjaya dimulakan!", Toast.LENGTH_SHORT).show()
                            } else {
                                serverStatusPref.summary = "❌ Gagal memulakan pelayan"
                                Toast.makeText(ctx, "❌ Gagal memulakan enjin pelayan", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    true
                }
            }
            serverCategory.addPreference(restartServerPref)

            val serverUrlPref = EditTextPreference(ctx).apply {
                key = PREF_PM_SERVER_URL
                title = "URL Pelayan Tersuai"
                dialogTitle = "Masukkan URL Pelayan"
                dialogMessage = "Lalai: http://127.0.0.1:8088 (Enjin Terbina Dalam). Untuk menyambung ke komputer di LAN, masukkan contoh: http://192.168.1.50:8088."
                setDefaultValue(DEFAULT_PM_SERVER_URL)
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val text = pref.text
                    if (text.isNullOrBlank()) DEFAULT_PM_SERVER_URL else text
                }
            }
            serverCategory.addPreference(serverUrlPref)

            val testServerPref = Preference(ctx).apply {
                title = "Uji Sambungan Pelayan"
                summary = "Semak status sambungan pelayan port 8088 / URL tersuai"
                setOnPreferenceClickListener {
                    val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val currentUrl = p.getString(PREF_PM_SERVER_URL, DEFAULT_PM_SERVER_URL)
                        ?.trimEnd('/') ?: DEFAULT_PM_SERVER_URL

                    Toast.makeText(ctx, "Menyemak sambungan ke $currentUrl...", Toast.LENGTH_SHORT).show()

                    thread {
                        var isOnline = false
                        var versionInfo = ""
                        try {
                            val u = URL("$currentUrl/api/version")
                            val conn = u.openConnection() as HttpURLConnection
                            conn.connectTimeout = 3500
                            conn.readTimeout = 3500
                            conn.requestMethod = "GET"
                            conn.connect()
                            val code = conn.responseCode
                            if (code in 200..299) {
                                isOnline = true
                                versionInfo = conn.inputStream.bufferedReader().use { it.readText() }
                            }
                            conn.disconnect()
                        } catch (e: Exception) {
                            versionInfo = e.message ?: "Ralat sambungan"
                        }

                        Handler(Looper.getMainLooper()).post {
                            if (isOnline) {
                                serverStatusPref.summary = "✅ Aktif & Bersedia di $currentUrl"
                                Toast.makeText(ctx, "Pelayan Aktif! Versi: $versionInfo", Toast.LENGTH_LONG).show()
                            } else {
                                serverStatusPref.summary = "⚠️ TIDAK dapat dihubungi di $currentUrl"
                                Toast.makeText(ctx, "Pelayan TIDAK dapat dihubungi di $currentUrl.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    true
                }
            }
            serverCategory.addPreference(testServerPref)

            // Kategori 2: Sesi & Pengesahan Telegram
            val tgCategory = PreferenceCategory(ctx).apply {
                title = "Log Masuk PencariMovie & Telegram"
            }
            screen.addPreference(tgCategory)

            val tgStatusPref = Preference(ctx).apply {
                title = "Status Sesi & Kuki"
                isEnabled = false
            }
            tgCategory.addPreference(tgStatusPref)

            fun updateTgStatus() {
                val p = PreferenceManager.getDefaultSharedPreferences(ctx)
                val cookies = p.getString(PREF_PM_TG_COOKIES, null)
                val hasCookies = !cookies.isNullOrBlank()
                tgStatusPref.summary = if (hasCookies) {
                    val preview = if (cookies!!.length > 40) cookies.substring(0, 37) + "..." else cookies
                    "Aktif ($preview)"
                } else {
                    "Belum Log Masuk (Klik butang di bawah untuk log masuk)"
                }
            }
            updateTgStatus()

            val tgLoginPref = Preference(ctx).apply {
                title = "Log Masuk PencariMovie (Telegram Widget / Web)"
                summary = "Buka tetingkap log masuk flixtorbot.pencarimovie.com untuk log masuk dan simpan kuki sesi"
                setOnPreferenceClickListener {
                    val dialog = TelegramCaptureDialog()
                    dialog.onSaved = {
                        updateTgStatus()
                    }
                    dialog.show(parentFragmentManager, "TelegramWVCapture")
                    true
                }
            }
            tgCategory.addPreference(tgLoginPref)

            val manualCookiePref = EditTextPreference(ctx).apply {
                key = PREF_PM_TG_COOKIES
                title = "Kuki Sesi Manual (Pilihan)"
                dialogTitle = "Masukkan Kuki Sesi"
                dialogMessage = "Anda boleh menampal kuki sesi anda di sini secara manual sekiranya diperlukan."
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val text = pref.text
                    if (text.isNullOrBlank()) "Tiada kuki manual" else if (text.length > 50) text.substring(0, 47) + "..." else text
                }
                setOnPreferenceChangeListener { _, _ ->
                    Handler(Looper.getMainLooper()).postDelayed({ updateTgStatus() }, 200)
                    true
                }
            }
            tgCategory.addPreference(manualCookiePref)

            val clearTgPref = Preference(ctx).apply {
                title = "Padam Sesi & Kuki"
                summary = "Kosongkan kuki sesi yang tersimpan di peranti ini"
                setOnPreferenceClickListener {
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                        .remove(PREF_PM_TG_COOKIES)
                        .apply()
                    try {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    } catch (_: Exception) {}
                    updateTgStatus()
                    Toast.makeText(ctx, "Sesi dan kuki telah dipadamkan.", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            tgCategory.addPreference(clearTgPref)

            // Kategori 3: Navigasi
            val closeCategory = PreferenceCategory(ctx).apply {
                title = "Selesai"
            }
            screen.addPreference(closeCategory)

            val closePref = Preference(ctx).apply {
                title = "Tutup Tetapan"
                setOnPreferenceClickListener {
                    (parentFragment as? DialogFragment)?.dismiss()
                    true
                }
            }
            closeCategory.addPreference(closePref)
        }
    }

    companion object {
        fun open(context: Context) {
            val activity = getActivity(context)
            if (activity is FragmentActivity) {
                val dialog = PencariMovieSettingsDialog()
                dialog.show(activity.supportFragmentManager, "PencariMovieSettingsDialog")
            } else {
                Toast.makeText(context, "Sila buka dari antaramuka utama", Toast.LENGTH_SHORT).show()
            }
        }

        private fun getActivity(context: Context?): FragmentActivity? {
            var ctx = context
            while (ctx is ContextWrapper) {
                if (ctx is FragmentActivity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}

class TelegramCaptureDialog : DialogFragment() {

    var onSaved: (() -> Unit)? = null
    private var webView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val topBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(requireContext()).apply {
            text = "Log Masuk Telegram PencariMovie"
            setTextColor(Color.WHITE)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        topBar.addView(title)

        val saveBtn = Button(requireContext()).apply {
            text = "Simpan Kuki"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                captureAndSaveCookies()
            }
        }
        topBar.addView(saveBtn)

        val closeBtn = Button(requireContext()).apply {
            text = "Tutup"
            setBackgroundColor(Color.parseColor("#374151"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                dismiss()
            }
        }
        topBar.addView(closeBtn)

        root.addView(topBar)

        val frame = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        val wv = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setupWv(this)
        }
        this.webView = wv
        frame.addView(wv)

        root.addView(frame)

        wv.loadUrl(PM_LOGIN_URL)

        return root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWv(wv: WebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(false)
        settings.userAgentString = PencariMovie.USER_AGENT
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                if (url.startsWith("tg://") || url.startsWith("https://t.me/")) {
                    try {
                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        startActivity(browserIntent)
                        return true
                    } catch (_: Exception) {}
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val cookies = CookieManager.getInstance().getCookie(url ?: PM_MAIN_URL)
                if (!cookies.isNullOrBlank() && (cookies.contains("wordpress_logged_in") || cookies.contains("tg_auth"))) {
                    saveCookies(cookies)
                }
            }
        }
    }

    private fun captureAndSaveCookies() {
        val cookies = CookieManager.getInstance().getCookie(PM_MAIN_URL)
            ?: CookieManager.getInstance().getCookie(PM_LOGIN_URL)
        if (!cookies.isNullOrBlank()) {
            saveCookies(cookies)
            Toast.makeText(requireContext(), "Kuki sesi berjaya disimpan!", Toast.LENGTH_SHORT).show()
            dismiss()
        } else {
            Toast.makeText(requireContext(), "Tiada kuki dikesan. Sila log masuk dahulu.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCookies(cookies: String) {
        val p = PreferenceManager.getDefaultSharedPreferences(requireContext())
        p.edit().putString(PREF_PM_TG_COOKIES, cookies).apply()
        onSaved?.invoke()
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}
