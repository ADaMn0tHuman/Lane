package app.lanewidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Самообновление: берёт последний релиз на GitHub, сравнивает номер сборки с установленным
 * и, если новее, скачивает APK и открывает системный установщик. Подпись одна и та же,
 * поэтому обновление ставится поверх без удаления.
 */
object UpdateChecker {

    private const val LATEST = "https://api.github.com/repos/ADaMn0tHuman/Lane/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    fun check(ctx: Context) {
        val app = ctx.applicationContext
        toast(app, app.getString(R.string.update_checking))
        Thread {
            try {
                val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "LaneWidget")
                }
                if (conn.responseCode != 200) {
                    toast(app, app.getString(R.string.update_error, "HTTP ${conn.responseCode}"))
                    return@Thread
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

                val remote = json.optString("tag_name").removePrefix("v").toLongOrNull() ?: 0L
                val local = PackageInfoCompat.getLongVersionCode(
                    app.packageManager.getPackageInfo(app.packageName, 0),
                )
                if (remote <= local) {
                    toast(app, app.getString(R.string.update_none))
                    return@Thread
                }

                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.getString("browser_download_url")
                        break
                    }
                }
                if (apkUrl == null) {
                    toast(app, app.getString(R.string.update_no_apk))
                    return@Thread
                }

                toast(app, app.getString(R.string.update_found))
                val file = File(app.cacheDir, "update/lane-update.apk")
                file.parentFile?.mkdirs()
                (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }.inputStream.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                if (!signaturesMatch(app, file)) {
                    file.delete()
                    toast(app, app.getString(R.string.update_sig_mismatch))
                    return@Thread
                }
                main.post { install(app, file) }
            } catch (e: Exception) {
                toast(app, app.getString(R.string.update_error, e.message ?: e.javaClass.simpleName))
            }
        }.start()
    }

    /** Подпись скачанного APK должна совпадать с подписью установленной версии, иначе установка не пройдёт. */
    @Suppress("DEPRECATION")
    private fun signaturesMatch(ctx: Context, file: File): Boolean {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = digests(pm.getPackageInfo(ctx.packageName, flags))
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val other = digests(archive)
        return installed.isNotEmpty() && installed == other
    }

    @Suppress("DEPRECATION")
    private fun digests(info: PackageInfo): Set<String> {
        val sigs: Array<Signature>? =
            if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return sigs.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun install(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            toast(ctx, ctx.getString(R.string.update_need_permission))
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun toast(ctx: Context, text: String) {
        main.post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }
}
