package com.mts.pencarimovie

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object PencariMovieServerManager {

    private const val TAG = "PMServerMgr"
    const val SERVER_PORT = 8088
    const val SERVER_HOST = "0.0.0.0"
    const val LOCAL_URL = "http://127.0.0.1:$SERVER_PORT"

    private var serverProcess: Process? = null
    private val isStarting = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Semak sama ada pelayan tempatan port 8088 aktif dan menjawab
     */
    fun pingServer(urlStr: String = LOCAL_URL, timeoutMs: Int = 1500): Boolean {
        return try {
            val pingUrl = "${urlStr.trimEnd('/')}/api/version"
            val conn = URL(pingUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mulakan pelayan secara asynchronous (latar belakang)
     */
    fun startServerAsync(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        thread(name = "PMServerStartThread", isDaemon = true) {
            val ok = startServer(context.applicationContext)
            onResult?.invoke(ok)
        }
    }

    /**
     * Mulakan pelayan tempatan Rust secara synchronous
     */
    @Synchronized
    fun startServer(context: Context): Boolean {
        if (pingServer()) {
            Log.i(TAG, "PencariMovie Server is already running and responding on port $SERVER_PORT")
            acquireLocks(context)
            return true
        }

        if (isStarting.getAndSet(true)) {
            Log.i(TAG, "Server startup is already in progress, waiting...")
            for (i in 1..20) {
                Thread.sleep(500)
                if (pingServer()) return true
            }
            return pingServer()
        }

        try {
            acquireLocks(context)

            val nativeDir = File(context.applicationInfo.nativeLibraryDir)
            val rustBin = File(nativeDir, "libpencarimovie_rust.so")

            if (!rustBin.exists()) {
                Log.e(TAG, "CRITICAL: Rust binary not found at ${rustBin.absolutePath}")
                isStarting.set(false)
                return false
            }

            try {
                rustBin.setExecutable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set executable bit: ${e.message}")
            }

            // Direktori kerja dan storan sementara
            val pmDir = File(context.filesDir, "pm").apply { mkdirs() }
            val tmpDir = File(context.cacheDir, "tmp").apply { mkdirs() }

            // Salin sijil SSL jika belum wujud
            extractCertIfMissing(context, pmDir)

            // Bina arahan pelaksanaan
            val cmd = arrayListOf(
                rustBin.absolutePath,
                "--port", SERVER_PORT.toString(),
                "--host", SERVER_HOST,
                "--workers", "8",
                "--inflight", "32",
                "--app-version", "1.0.117"
            )

            Log.i(TAG, "Launching Rust server: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            pb.directory(pmDir)
            pb.redirectErrorStream(true)

            val env = pb.environment()
            env["PM_APP_ROOT"] = pmDir.absolutePath
            env["HOME"] = pmDir.absolutePath
            env["TMPDIR"] = tmpDir.absolutePath

            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "9774d56d682e549c"
            } catch (_: Exception) {
                "9774d56d682e549c"
            }
            env["PM_DEVICE_ID"] = androidId
            env["PATH"] = "${nativeDir.absolutePath}:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = nativeDir.absolutePath

            val cloudflaredBin = File(nativeDir, "libcloudflared.so")
            if (cloudflaredBin.exists()) {
                try { cloudflaredBin.setExecutable(true, false) } catch (_: Exception) {}
                env["PM_CLOUDFLARED_BIN"] = cloudflaredBin.absolutePath
                Log.i(TAG, "libcloudflared.so enabled at ${cloudflaredBin.absolutePath}")
            }

            val certFile = File(pmDir, "cacert.pem")
            if (certFile.exists()) {
                env["SSL_CERT_FILE"] = certFile.absolutePath
            }

            // Mulakan proses
            val proc = pb.start()
            serverProcess = proc

            // Baca output proses di latar belakang agar paip input/output tidak tergantung
            thread(name = "PMServerLogPipe", isDaemon = true) {
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.d("PM:RustCore", line)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Tunggu sehingga pelayan bersedia
            var ready = false
            for (i in 1..40) {
                Thread.sleep(300)
                if (pingServer(LOCAL_URL, timeoutMs = 800)) {
                    ready = true
                    Log.i(TAG, "✅ PencariMovie Rust Server is UP and READY on port $SERVER_PORT (attempt $i)")
                    break
                }
            }

            if (!ready) {
                Log.e(TAG, "❌ Server did not respond within 12s.")
            }

            return ready

        } catch (e: Exception) {
            Log.e(TAG, "Error starting PencariMovie Rust Server", e)
            return false
        } finally {
            isStarting.set(false)
        }
    }

    /**
     * Salin fail cacert.pem daripada aset ke direktori data jika belum ada
     */
    private fun extractCertIfMissing(context: Context, pmDir: File) {
        try {
            val certFile = File(pmDir, "cacert.pem")
            if (!certFile.exists()) {
                context.assets.open("cacert.pem").use { input ->
                    FileOutputStream(certFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Copied cacert.pem to ${certFile.absolutePath}")
            }
            // Juga letakkan di storage/tunnel jika diperlukan
            val tunnelDir = File(pmDir, "storage/tunnel").apply { mkdirs() }
            val tunnelCert = File(tunnelDir, "cacert.pem")
            if (!tunnelCert.exists() && certFile.exists()) {
                certFile.copyTo(tunnelCert, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract cacert.pem: ${e.message}")
        }
    }

    /**
     * Kunci kuasa & wifi agar penstriman lancar tanpa gangguan sistem Android
     */
    private fun acquireLocks(context: Context) {
        try {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pencarimovie:server_wakelock")?.apply {
                    setReferenceCounted(false)
                    acquire(24 * 3600 * 1000L)
                }
            }
            if (wifiLock == null) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pencarimovie:wifi")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lock acquire warning: ${e.message}")
        }
    }

    /**
     * Hentikan pelayan sekiranya aplikasi ditutup
     */
    @Synchronized
    fun stopServer() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null

            serverProcess?.let { proc ->
                Log.i(TAG, "Stopping PencariMovie Rust Server process...")
                proc.destroy()
                try {
                    Thread.sleep(300)
                    proc.destroyForcibly()
                } catch (_: Exception) {}
            }
            serverProcess = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    fun isRunning(): Boolean = pingServer()
}
