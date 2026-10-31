package it.snel.kaartscan

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * A published build: a GitHub release (tag v1.10, version 1.10) or, for
 * the debug app, the latest debug build ([build] is its CI run number).
 */
data class Release(val tag: String, val version: String, val apkUrl: String, val build: Long? = null) {
    /** shown to the user */
    val label: String get() = if (build != null) "$version (build $build)" else version
}

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
 * Finds and downloads new versions on GitHub. The release app follows the
 * releases, which CI creates for every v* tag with kaartscan-<tag>.apk
 * attached; the debug app follows the pre-release 'debug', which CI
 * replaces on every push to main with kaartscan-debug.apk and
 * kaartscan-debug.json ({"version", "build", "commit"}).
 *
 * The latest release is found through the redirect of /releases/latest to
 * /releases/tag/<tag>, not the GitHub API: that has a limit of 60 requests
 * per hour per IP address, which phones on the school network share. A
 * pre-release is never 'latest', so release apps never see debug builds.
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

    private val info = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    val currentVersion: String = info?.versionName ?: "dev"

    /** the CI run number that built this app */
    val currentBuild: Long = info?.let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else legacyVersionCode(it).toLong()
    } ?: 0

    /** "1.10" for the release app, "1.10-5-gabc1234 (build 57)" for the debug app */
    val currentLabel: String = if (BuildConfig.DEBUG) "$currentVersion (build $currentBuild)" else currentVersion

    /** true when [release] is newer than this app; null when that cannot be told (a local dev build) */
    fun isNewer(release: Release): Boolean? =
        if (release.build != null) release.build > currentBuild
        else Versions.isNewer(release.version, currentVersion)

    fun latest(): Release = if (BuildConfig.DEBUG) latestDebug() else latestRelease()

    private fun latestDebug(): Release {
        val request = Request.Builder()
            .url("$REPO/releases/download/$DEBUG_TAG/kaartscan-debug.json")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val body = http.newCall(request).execute().use { r ->
            if (r.code == 404) throw IOException("Er is nog geen debugbuild gepubliceerd")
            if (!r.isSuccessful) throw IOException("GitHub antwoordde onverwacht (HTTP ${r.code})")
            r.body?.string() ?: ""
        }
        return try {
            val o = JSONObject(body)
            Release(
                tag = DEBUG_TAG,
                version = o.getString("version"),
                apkUrl = "$REPO/releases/download/$DEBUG_TAG/kaartscan-debug.apk",
                build = o.getLong("build"),
            )
        } catch (e: JSONException) {
            throw IOException("Ongeldige gegevens bij de debugbuild")
        }
    }

    private fun latestRelease(): Release {
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
        const val DEBUG_TAG = "debug"
        private const val KEY_AUTO_CHECK = "update_auto_check"
    }
}

@Suppress("DEPRECATION")
private fun legacyVersionCode(info: PackageInfo): Int = info.versionCode
