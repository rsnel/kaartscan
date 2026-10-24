package it.snel.kaartscan

import android.content.Context
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server showed its login page: no (valid) session, or the user is not an admin. */
class SessionExpiredException(message: String?) : IOException(message)

/** Username/password rejected by do_login. */
class LoginFailedException(message: String) : IOException(message)

/** error_system() output, an HTTP error, or something else unexpected. */
class ServerException(message: String) : IOException(message)

/**
 * Talks to the kaartscan web application like a browser would: it logs in
 * with the 'oldstyle' form (call.php?action=do_login), keeps the PHP session
 * cookie, scrapes the pages and posts to the do_*.php scripts.
 *
 * The do_*.php scripts report their outcome through $_SESSION['notice'] or
 * $_SESSION['err'], which is shown (and consumed) by the next page that calls
 * html_start(). So every write is: POST without following the redirect, then
 * GET the page the redirect would have gone to and read the message there.
 * That also works when the redirect header itself fails because the script
 * produced output (print_r) before calling header().
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

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            var v = value.trim().ifEmpty { DEFAULT_BASE_URL }
            if (!v.endsWith("/")) v += "/"
            prefs.edit().putString(KEY_BASE_URL, v).apply()
        }

    var lastUsername: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    fun hasSession(): Boolean = cookieJar.hasCookies()

    // ---- high level operations -------------------------------------------------

    /** Returns the username the server reports as logged in. */
    fun login(username: String, password: String): String {
        cookieJar.clear()
        lastUsername = username
        val r = post("call.php?action=do_login", listOf("username" to username, "password" to password))
        if (r.code == 200 && isLoginPage(r.body)) {
            // no redirect: show whatever the login page says
            throw LoginFailedException(Html.parse(r.body, r.url.toString()).error?.toString() ?: "Inloggen mislukt")
        }
        val page = try {
            getPage("index.php")
        } catch (e: SessionExpiredException) {
            // do_login() and check_admin() report problems via error_user(),
            // which ends up as an error message on the login page
            throw LoginFailedException(e.message ?: "Inloggen mislukt")
        }
        return page.loggedInAs ?: username
    }

    /** Returns the logged in username if the stored session is still valid. */
    fun checkSession(): String? = try {
        getPage("index.php").loggedInAs ?: lastUsername
    } catch (e: SessionExpiredException) {
        cookieJar.clear()
        null
    }

    fun logout() {
        try {
            get(url("call.php?action=do_logout"), follow = false)
        } catch (e: IOException) {
            // we forget the session anyway
        }
        cookieJar.clear()
    }

    fun kaartverkoopPage(): Page = getPage("kaartverkoop.php")

    fun kaartcontrolePage(): Page = getPage("kaartcontrole.php")

    fun overzichtPage(showDeleted: Boolean, alphabetic: Boolean): Page {
        val q = StringBuilder("overzicht.php?")
        if (showDeleted) q.append("toon_verwijderd=on&")
        q.append("order=").append(if (alphabetic) "alph" else "chron")
        return getPage(q.toString())
    }

    /** do_kaartverkoop.php; returns the refreshed kaartverkoop page with notice/err. */
    fun verkoop(barcode: String, introducee: Boolean): Page {
        val form = mutableListOf("barcode" to barcode)
        if (introducee) form.add("introducee" to "1")
        checkNotLoginPage(post("do_kaartverkoop.php", form))
        return kaartverkoopPage()
    }

    /** do_kaartcontrole.php; returns the refreshed kaartcontrole page with notice/err. */
    fun controle(barcode: String): Page {
        checkNotLoginPage(post("do_kaartcontrole.php", listOf("barcode" to barcode)))
        return kaartcontrolePage()
    }

    /** Follows a 'verwijderen' link (do_delete.php / do_delete_kaartcontrole.php). */
    fun delete(link: String) {
        val target = link.toHttpUrlOrNull() ?: throw ServerException("Ongeldige link: $link")
        val base = url("")
        if (target.host != base.host || !target.encodedPath.startsWith(base.encodedPath))
            throw ServerException("Link wijst naar andere server: $link")
        checkNotLoginPage(get(target, follow = false))
    }

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

    private fun execute(request: Request): Response =
        http.newCall(request).execute().use { r ->
            Response(r.request.url, r.code, r.header("Content-Type"), r.header("Location"), r.body?.string() ?: "")
        }

    private fun get(target: HttpUrl, follow: Boolean = true): Response {
        var current = target
        repeat(10) {
            val r = execute(Request.Builder().url(current).get().build())
            if (follow && r.code in 300..399) {
                val loc = r.location ?: throw ServerException("Redirect zonder Location-header")
                current = current.resolve(loc) ?: throw ServerException("Ongeldige redirect: $loc")
                return@repeat
            }
            return check(r)
        }
        throw ServerException("Te veel redirects")
    }

    private fun post(path: String, form: List<Pair<String, String>>): Response {
        val body = FormBody.Builder()
        for ((k, v) in form) body.add(k, v)
        return check(execute(Request.Builder().url(url(path)).post(body.build()).build()))
    }

    private fun check(r: Response): Response {
        if (r.code >= 400) throw ServerException("HTTP ${r.code} van de server")
        // error_system() answers 200 text/plain
        if (r.code == 200 && r.contentType?.startsWith("text/plain") == true && r.body.contains("systeemfout")) {
            throw ServerException(r.body.lineSequence().filter { it.isNotBlank() && !it.startsWith("---") }.take(2).joinToString("\n"))
        }
        return r
    }

    private fun isLoginPage(html: String): Boolean = html.contains("Loginpagina</title>")

    private fun checkNotLoginPage(r: Response) {
        if (r.code == 200 && isLoginPage(r.body))
            throw SessionExpiredException(Html.parse(r.body, r.url.toString()).error?.toString())
    }

    private fun getPage(path: String): Page {
        val r = get(url(path))
        val page = Html.parse(r.body, r.url.toString())
        if (page.isLoginPage) throw SessionExpiredException(page.error?.toString())
        return page
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://srv-ovc-linux.ovc.nl/kaartscan/"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USERNAME = "username"
    }
}
