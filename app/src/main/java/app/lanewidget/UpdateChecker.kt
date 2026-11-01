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

    private const val REPO = "https://github.com/ADaMn0tHuman/Lane"
    private val main = Handler(Looper.getMainLooper())

    /**
     * Проверка идёт через обычную страницу релизов, а не через API. У API GitHub лимит
     * примерно 60 запросов в час с одного адреса, и он давал ошибку 403. Страница
     * /releases/latest перенаправляет на /releases/tag/<тег>, из тега получаем номер сборки
     * и имя APK (оно такое же, как в workflow: lane-widget-<номер>.apk).
     */
    fun check(ctx: Context) {
        val app = ctx.applicationContext
        toast(app, app.getString(R.string.update_checking))
        Thread {
            try {
                val probe = (URL("$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "LaneWidget")
                }
                val code = probe.responseCode
                val finalUrl = probe.url.toString()
                probe.disconnect()
                if (code != 200 || !finalUrl.contains("/releases/tag/")) {
                    toast(app, app.getString(R.string.update_error, "HTTP $code"))
                    return@Thread
                }

                val tag = finalUrl.substringAfterLast("/releases/tag/")
                val remote = tag.removePrefix("v").toLongOrNull() ?: 0L
                val local = PackageInfoCompat.getLongVersionCode(
                    app.packageManager.getPackageInfo(app.packageName, 0),
                )
                if (remote <= local) {
                    toast(app, app.getString(R.string.update_none))
                    return@Thread
                }

                toast(app, app.getString(R.string.update_found))
                val apkUrl = "$REPO/releases/download/$tag/lane-widget-$remote.apk"
                val file = File(app.cacheDir, "update/lane-update.apk")
                file.parentFile?.mkdirs()
                val dl = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "LaneWidget")
                }
                if (dl.responseCode != 200) {
                    toast(app, app.getString(R.string.update_no_apk))
                    dl.disconnect()
                    return@Thread
                }
                dl.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                dl.disconnect()

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
