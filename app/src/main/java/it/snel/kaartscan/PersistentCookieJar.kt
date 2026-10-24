package it.snel.kaartscan

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cookie jar that survives app restarts, so the PHP session cookie
 * (named 'kaartscan', see config_common.php) is kept in SharedPreferences.
 */
class PersistentCookieJar(private val prefs: SharedPreferences) : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    init {
        load()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            // a cookie with an expiry date in the past is a deletion
            if (c.expiresAt > now) this.cookies.add(c)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        persist()
    }

    @Synchronized
    fun hasCookies(): Boolean = cookies.isNotEmpty()

    private fun persist() {
        val arr = JSONArray()
        for (c in cookies) {
            val url = (if (c.secure) "https://" else "http://") + c.domain + c.path
            arr.put(JSONObject().put("url", url).put("cookie", c.toString()))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load() {
        val json = prefs.getString(KEY, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.getString("url").toHttpUrlOrNull() ?: continue
                Cookie.parse(url, o.getString("cookie"))?.let { cookies.add(it) }
            }
        } catch (e: Exception) {
            cookies.clear()
        }
    }

    companion object {
        private const val KEY = "cookies"
    }
}
