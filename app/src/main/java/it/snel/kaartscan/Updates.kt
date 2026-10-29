package it.snel.kaartscan

import android.content.Context
import android.content.pm.PackageManager
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** A published GitHub release: tag v1.10, version 1.10, and its APK. */
data class Release(val tag: String, val version: String, val apkUrl: String)

/** Version names as the build makes them with git describe: "1.10", "1.9-3-gabc1234", "1.9-dirty". */
object Versions {
    private val pattern = Regex("^v?(\\d+(?:\\.\\d+)+)(-\\d+-g[0-9a-f]+)?(-dirty)?$")

    private fun parts(version: String): List<Int>? =
        pattern.find(version.trim())?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

    /**
     * true when [remote] is newer than [current]; null when [current] is no
     * recognisable version (a local "dev" build). "1.9-3-gabc1234", built
     * after tag v1.9, counts as 1.9, so release 1.9 is not offered to it.
     */
    fun isNewer(remote: String, current: String): Boolean? {
        val r = parts(remote) ?: return false
        val c = parts(current) ?: return null
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

/**
 * Finds and downloads new versions from the GitHub releases, which CI
 * creates for every v* tag with kaartscan-<tag>.apk attached.
 *
 * The latest release is found through the redirect of /releases/latest to
 * /releases/tag/<tag>, not the GitHub API: that has a limit of 60 requests
 * per hour per IP address, which phones on the school network share.
 *
 * Blocking; call from a background thread.
 */
class Updater(private val context: Context) {
    private val prefs = context.getSharedPreferences("kaartscan", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var download: Call? = null

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    val currentVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev"
    } catch (e: PackageManager.NameNotFoundException) {
        "dev"
    }

    fun latest(): Release {
        val noRedirects = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val (code, location) = noRedirects.newCall(Request.Builder().url("$REPO/releases/latest").get().build())
            .execute().use { it.code to it.header("Location") }
        val tag = location?.let { Regex("/releases/tag/([^/?#]+)").find(it)?.groupValues?.get(1) }
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: throw IOException(
                if (code in 300..399) "Er is nog geen versie gepubliceerd" else "GitHub antwoordde onverwacht (HTTP $code)"
            )
        return Release(tag, tag.removePrefix("v"), "$REPO/releases/download/$tag/kaartscan-$tag.apk")
    }

    /** Downloads the APK into the cache; [onProgress] gets 0..1, or null when the size is unknown. */
    fun download(release: Release, onProgress: (Float?) -> Unit): File {
        val dir = File(context.cacheDir, "updates")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "kaartscan-${release.tag}.apk")
        val call = http.newCall(Request.Builder().url(release.apkUrl).get().build())
        download = call
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw IOException("Downloaden mislukt (HTTP ${r.code})")
                val body = r.body ?: throw IOException("Downloaden mislukt: leeg antwoord")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReported = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(done.toFloat() / total)
                                }
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw if (call.isCanceled()) IOException("Downloaden geannuleerd") else e
        } finally {
            download = null
        }
        // Android refuses an update with another signature or package itself;
        // this only catches a broken or wrong file early
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null || info.packageName != context.packageName) {
            file.delete()
            throw IOException("Het gedownloade bestand is geen Kaartscan-app")
        }
        return file
    }

    fun cancel() {
        download?.cancel()
    }

    companion object {
        const val REPO = "https://github.com/rsnel/kaartscan"
        private const val KEY_AUTO_CHECK = "update_auto_check"
    }
}
