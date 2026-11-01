package app.lanewidget

import android.content.Context

/**
 * Какие приложения виджет игнорирует (видеосервисы, браузеры и т. п.).
 * Список по умолчанию лежит здесь, а на экране Lane Widget любой плеер можно скрыть или вернуть
 * вручную. Ручной выбор сильнее списка по умолчанию.
 */
object Blocklist {

    private const val PREFS = "lane_widget"
    private const val KEY_HIDE = "hidden_pkgs"
    private const val KEY_SHOW = "shown_pkgs"

    private val EXACT = setOf(
        // YouTube и его неофициальные сборки
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.mango",
        "app.revanced.android.youtube",
        "com.vanced.android.youtube",
        "org.schabi.newpipe",
        "org.polymorphicshade.newpipe",
        // Видеосервисы и соцсети с видео
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.ss.android.ugc.aweme",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.snapchat.android",
        "com.pinterest",
        "video.like",
        "tv.twitch.android.app",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus",
        "com.hulu.plus",
        "ru.rutube.app",
        "ru.kinopoisk",
        "ru.ivi.client",
        "ru.more.play",
        "ru.rt.video.app.mobile",
        "ru.yandex.zen",
        "ru.yandex.searchplugin",
        // Видеоплееры и галереи
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.google.android.videos",
        "com.google.android.apps.photos",
        "com.miui.video",
        "com.miui.videoplayer",
        "com.miui.gallery",
        "com.samsung.android.video",
        "com.sec.android.gallery3d",
    )

    private val PREFIXES = listOf(
        "com.vkontakte.", // ВКонтакте
        "com.vk.",        // VK Видео, VK Мессенджер и др.
        "org.mozilla.",   // Firefox, Fenix, Focus
        "com.chrome.",    // Chrome Beta / Dev / Canary
        "com.opera.",
        "com.yandex.browser",
    )

    private val KEYWORDS = listOf("browser")

    private val EXTRA_EXACT = setOf(
        "com.android.chrome",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.UCMobile.intl",
        "com.cloudmosa.puffinFree",
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBlockedByDefault(pkg: String): Boolean =
        pkg in EXACT || pkg in EXTRA_EXACT ||
            PREFIXES.any { pkg.startsWith(it) } ||
            KEYWORDS.any { pkg.contains(it, ignoreCase = true) }

    fun isBlocked(ctx: Context, pkg: String): Boolean {
        val p = prefs(ctx)
        if (pkg in p.getStringSet(KEY_SHOW, emptySet()).orEmpty()) return false
        if (pkg in p.getStringSet(KEY_HIDE, emptySet()).orEmpty()) return true
        return isBlockedByDefault(pkg)
    }

    fun setBlocked(ctx: Context, pkg: String, blocked: Boolean) {
        val p = prefs(ctx)
        val hide = HashSet(p.getStringSet(KEY_HIDE, emptySet()).orEmpty())
        val show = HashSet(p.getStringSet(KEY_SHOW, emptySet()).orEmpty())
        if (blocked) {
            hide.add(pkg)
            show.remove(pkg)
        } else {
            show.add(pkg)
            hide.remove(pkg)
        }
        p.edit().putStringSet(KEY_HIDE, hide).putStringSet(KEY_SHOW, show).apply()
    }
}
