package it.snel.kaartscan

import android.content.Context
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** No (valid) session (api.php 401), or the user is not an admin (403). */
class SessionExpiredException(message: String?) : IOException(message)

/** Username/password rejected by do_login, or not an admin. */
class LoginFailedException(message: String) : IOException(message)

/** error_system() output, an HTTP error, or something else unexpected. */
class ServerException(message: String) : IOException(message)

/** An error answer from api.php ({"error": ...} with status 400). */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Talks to the kaartscan web application: logs in with the 'oldstyle' form
 * (call.php?action=do_login) and keeps the PHP session cookie, reads through
 * api.php (JSON) and writes through the do_*.php scripts, like the website.
 *
 * The do_*.php scripts report their outcome in $_SESSION['notice'] or
 * $_SESSION['err']; api.php?action=melding returns and clears it. api.php
 * relies on phplib's $json_api: without a session it answers 401, for a
 * non-admin 403, both as JSON.
 *
 * All methods block; call them from a background thread.
 */
class KaartscanClient(context: Context) {
    private val prefs = context.getSharedPreferences("kaartscan", Context.MODE_PRIVATE)
    private val cookieJar = PersistentCookieJar(prefs)
    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS) // login waits for the auth server
        .build()

    /** outcome of every request, for the status dot */
    val stats = ServerStats()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            var v = value.trim().ifEmpty { DEFAULT_BASE_URL }
            if (!v.endsWith("/")) v += "/"
            prefs.edit().putString(KEY_BASE_URL, v).apply()
        }

    var scanSensitivity: ScanSensitivity
        get() = ScanSensitivity.fromName(prefs.getString(KEY_SENSITIVITY, null))
        set(value) = prefs.edit().putString(KEY_SENSITIVITY, value.name).apply()

    var lastUsername: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    fun hasSession(): Boolean = cookieJar.hasCookies()

    // ---- session ---------------------------------------------------------------

    fun login(username: String, password: String): Me {
        cookieJar.clear()
        stats.reset()
        lastUsername = username
        // do_login() always redirects; whether it worked shows in whoami
        post("call.php?action=do_login", listOf("username" to username, "password" to password))
        try {
            return me()
        } catch (e: SessionExpiredException) {
            // a 403 (not an admin) carries its reason; otherwise do_login()
            // left the reason in the session, which only the login page shows
            throw LoginFailedException(e.message ?: loginPageError() ?: "Inloggen mislukt")
        }
    }

    /** The stored session, if it is still valid. */
    fun checkSession(): Me? = try {
        me()
    } catch (e: SessionExpiredException) {
        cookieJar.clear()
        null
    }

    fun logout() {
        try {
            execute(Request.Builder().url(url("call.php?action=do_logout")).get().build())
        } catch (e: IOException) {
            // we forget the session anyway
        }
        cookieJar.clear()
    }

    /**
     * Is the server alive? For the status dot when there is no session:
     * api.php answers 401 JSON without logging anything, which means the
     * server works. The outcome only goes to [stats].
     */
    fun ping() {
        try {
            val r = execute(Request.Builder().url(url("api.php?action=whoami")).get().build())
            if (r.code < 500 && r.contentType?.startsWith("application/json") != true)
                stats.unexpected("Onverwacht antwoord van api.php (HTTP ${r.code})")
        } catch (e: IOException) {
            // counted in stats by execute()
        }
    }

    /** why do_login() refused: error_user() put it in the session, only the login page shows it */
    private fun loginPageError(): String? = try {
        Html.parse(get(url("index.php")).body).error?.toString()
    } catch (e: IOException) {
        null
    }

    // ---- reading: api.php ------------------------------------------------------

    fun me(): Me = Api.me(api("action=whoami"))

    /** [order]: chron (sale time), aankomst (arrival time) or alph */
    fun kaartjes(order: String = "chron", showDeleted: Boolean = false, limit: Int? = null): Listing {
        val q = StringBuilder("action=kaartverkoop&order=").append(order)
        if (showDeleted) q.append("&verwijderd=1")
        if (limit != null) q.append("&limit=").append(limit)
        val o = api(q.toString())
        return Listing(Api.kaartjes(o, baseUrl), Api.aantallenIn(o))
    }

    fun controles(limit: Int? = null): Listing {
        val o = api("action=kaartcontrole" + (limit?.let { "&limit=$it" } ?: ""))
        return Listing(Api.controles(o, baseUrl), Api.aantallenIn(o))
    }

    /** every sale, check-in, check-out and deleted sale, newest first; [zoek]: name or number */
    fun log(zoek: String = "", limit: Int = 500): List<LogEvent> {
        val q = StringBuilder("action=log&limit=").append(limit)
        if (zoek.isNotBlank()) q.append("&zoek=").append(java.net.URLEncoder.encode(zoek.trim(), "UTF-8"))
        return Api.log(api(q.toString()))
    }

    // ---- writing: do_*.php -----------------------------------------------------

    /** do_kaartverkoop.php; returns the server's message. */
    fun verkoop(barcode: String, introducee: Boolean): Message? {
        val form = mutableListOf("barcode" to barcode)
        if (introducee) form.add("introducee" to "1")
        checkNotLoginPage(post("do_kaartverkoop.php", form))
        return melding()
    }

    /** do_kaartcontrole.php; returns the server's message. */
    fun controle(barcode: String): Message? {
        checkNotLoginPage(post("do_kaartcontrole.php", listOf("barcode" to barcode)))
        return melding()
    }

    /** Follows a do_delete.php / do_delete_kaartcontrole.php link. */
    fun delete(link: String) {
        val target = link.toHttpUrlOrNull() ?: throw ServerException("Ongeldige link: $link")
        val base = url("")
        if (target.host != base.host || !target.encodedPath.startsWith(base.encodedPath))
            throw ServerException("Link wijst naar andere server: $link")
        val r = check(execute(Request.Builder().url(target).get().build()))
        checkNotLoginPage(r)
    }

    private fun melding(): Message? = Api.melding(api("action=melding"))

    // ---- HTTP plumbing ---------------------------------------------------------

    private class Response(
        val url: HttpUrl,
        val code: Int,
        val contentType: String?,
        val location: String?,
        val body: String,
    )

    private fun url(path: String): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull() ?: throw ServerException("Ongeldige server-URL: $baseUrl")
        return base.resolve(path) ?: throw ServerException("Ongeldig pad: $path")
    }

    private fun execute(request: Request): Response {
        val start = System.nanoTime()
        val response = try {
            http.newCall(request).execute().use { r ->
                Response(r.request.url, r.code, r.header("Content-Type"), r.header("Location"), r.body?.string() ?: "")
            }
        } catch (e: IOException) {
            stats.down(e.message?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.simpleName)
            throw e
        }
        if (response.code >= 500) stats.unexpected("HTTP ${response.code} op ${request.url.encodedPath}", countedAsOk = false)
        else stats.ok((System.nanoTime() - start) / 1_000_000)
        return response
    }

    /** a ServerException for an answer the server should not have given, counted in [stats] */
    private fun unexpected(message: String, alreadyCounted: Boolean = false): ServerException {
        if (!alreadyCounted) stats.unexpected(message)
        return ServerException(message)
    }

    /** GET api.php: JSON, errors as {"error": ...} with 401/403/400/500 */
    private fun api(query: String): JSONObject {
        val r = execute(Request.Builder().url(url("api.php?$query")).get().build())
        if (r.contentType?.startsWith("application/json") != true) {
            check(r) // HTTP errors
            throw unexpected("Onverwacht antwoord van api.php (HTTP ${r.code})", alreadyCounted = r.code >= 500)
        }
        val o = try {
            JSONObject(r.body)
        } catch (e: JSONException) {
            throw unexpected("Ongeldig antwoord van api.php", alreadyCounted = r.code >= 500)
        }
        val err = if (o.has("error")) o.optString("error") else null
        return when {
            r.code == 401 -> throw SessionExpiredException(null)
            r.code == 403 -> throw SessionExpiredException(err ?: "Geen toegang")
            r.code >= 500 -> throw unexpected(err ?: "HTTP ${r.code} van api.php", alreadyCounted = true)
            r.code >= 400 -> throw ApiException(r.code, err ?: "HTTP ${r.code} van api.php")
            else -> o
        }
    }

    private fun get(target: HttpUrl): Response {
        var current = target
        repeat(10) {
            val r = execute(Request.Builder().url(current).get().build())
            if (r.code in 300..399) {
                val loc = r.location ?: throw unexpected("Redirect zonder Location-header")
                current = current.resolve(loc) ?: throw unexpected("Ongeldige redirect: $loc")
                return@repeat
            }
            return check(r)
        }
        throw unexpected("Te veel redirects")
    }

    private fun post(path: String, form: List<Pair<String, String>>): Response {
        val body = FormBody.Builder()
        for ((k, v) in form) body.add(k, v)
        return check(execute(Request.Builder().url(url(path)).post(body.build()).build()))
    }

    private fun check(r: Response): Response {
        if (r.code >= 400) throw unexpected("HTTP ${r.code} van de server", alreadyCounted = r.code >= 500)
        // error_system() answers 200 text/plain
        if (r.code == 200 && r.contentType?.startsWith("text/plain") == true && r.body.contains("systeemfout")) {
            throw unexpected(r.body.lineSequence().filter { it.isNotBlank() && !it.startsWith("---") }.take(2).joinToString("\n"))
        }
        return r
    }

    /** do_*.php have no $json_api: without a session they show the login page */
    private fun checkNotLoginPage(r: Response) {
        if (r.code == 200 && Html.isLoginPage(r.body))
            throw SessionExpiredException(Html.parse(r.body).error?.toString())
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://srv-ovc-linux.ovc.nl/kaartscan/"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_SENSITIVITY = "scan_sensitivity"
    }
}
