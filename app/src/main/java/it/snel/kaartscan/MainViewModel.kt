package it.snel.kaartscan

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class Tab { VERKOOP, OVERZICHT, CONTROLE }

data class ScreenState(
    val page: Page? = null,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val result: Message? = null,
    /** increases with every new result, used to trigger sound/vibration once */
    val resultSeq: Int = 0,
    val feedbackPending: Boolean = false,
    val loadError: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = KaartscanClient(app)

    var loggedInAs by mutableStateOf<String?>(null)
        private set
    /** A stored session that is still valid, offered on the login screen. */
    var resumableUser by mutableStateOf<String?>(null)
        private set
    var checkingSession by mutableStateOf(false)
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    /** The 'introducee' checkbox of kaartverkoop; reset after a successful sale, like the web form. */
    var introducee by mutableStateOf(false)
    var showDeleted by mutableStateOf(false)
        private set
    var alphabetic by mutableStateOf(false)
        private set

    val baseUrl: String get() = client.baseUrl
    val lastUsername: String get() = client.lastUsername

    private val screens: Map<Tab, MutableState<ScreenState>> =
        Tab.entries.associateWith { mutableStateOf(ScreenState()) }

    private var lastScan: String? = null
    private var lastScanAt = 0L

    init {
        if (client.hasSession()) {
            checkingSession = true
            viewModelScope.launch {
                resumableUser = try {
                    io { client.checkSession() }
                } catch (e: Exception) {
                    null
                }
                checkingSession = false
            }
        }
    }

    fun state(tab: Tab): ScreenState = screens.getValue(tab).value

    private fun update(tab: Tab, f: (ScreenState) -> ScreenState) {
        val s = screens.getValue(tab)
        s.value = f(s.value)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---- login -----------------------------------------------------------------

    fun login(username: String, password: String, server: String) {
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                client.baseUrl = server
                val user = io { client.login(username.trim(), password) }
                resetScreens()
                resumableUser = null
                loggedInAs = user
            } catch (e: LoginFailedException) {
                loginError = e.message
            } catch (e: Exception) {
                loginError = failMessage(e)
            } finally {
                loginBusy = false
            }
        }
    }

    fun resume() {
        val user = resumableUser ?: return
        resetScreens()
        resumableUser = null
        loginError = null
        loggedInAs = user
    }

    fun logout() {
        loggedInAs = null
        resumableUser = null
        loginError = null
        resetScreens()
        viewModelScope.launch { io { client.logout() } }
    }

    private fun sessionExpired(e: SessionExpiredException) {
        loggedInAs = null
        resumableUser = null
        loginError = "Sessie verlopen of geen toegang" + (e.message?.let { " ($it)" } ?: "") + ". Log opnieuw in."
    }

    private fun resetScreens() {
        for (s in screens.values) s.value = ScreenState()
        introducee = false
        lastScan = null
    }

    private fun failMessage(e: Exception): String = when (e) {
        is ServerException -> e.message ?: "Serverfout"
        is IOException -> "Netwerkfout: ${e.message ?: e.javaClass.simpleName}"
        else -> "Fout: ${e.message ?: e.javaClass.simpleName}"
    }

    // ---- reading ---------------------------------------------------------------

    fun load(tab: Tab) {
        if (loggedInAs == null) return
        update(tab) { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val page = io { fetch(tab) }
                update(tab) { s ->
                    // a page can carry a message left in the session by an earlier action
                    val m = page.message
                    if (m != null) s.copy(page = page, loading = false, result = m, resultSeq = s.resultSeq + 1)
                    else s.copy(page = page, loading = false)
                }
            } catch (e: SessionExpiredException) {
                update(tab) { it.copy(loading = false) }
                sessionExpired(e)
            } catch (e: Exception) {
                update(tab) { it.copy(loading = false, loadError = failMessage(e)) }
            }
        }
    }

    private fun fetch(tab: Tab): Page = when (tab) {
        Tab.VERKOOP -> client.kaartverkoopPage()
        Tab.OVERZICHT -> client.overzichtPage(showDeleted, alphabetic)
        Tab.CONTROLE -> client.kaartcontrolePage()
    }

    fun updateShowDeleted(value: Boolean) {
        showDeleted = value
        load(Tab.OVERZICHT)
    }

    fun updateAlphabetic(value: Boolean) {
        alphabetic = value
        load(Tab.OVERZICHT)
    }

    // ---- writing ---------------------------------------------------------------

    /** Called for every barcode the camera sees; ignores the same code for a few seconds. */
    fun onScanned(tab: Tab, raw: String) {
        val code = Barcodes.normalize(raw)
        val now = SystemClock.elapsedRealtime()
        if (code == lastScan && now - lastScanAt < SAME_CODE_IGNORE_MS) return
        if (state(tab).submitting) return
        lastScan = code
        lastScanAt = now
        submit(tab, code)
    }

    fun submit(tab: Tab, input: String) {
        val code = Barcodes.normalize(input)
        if (code.isEmpty() || state(tab).submitting) return
        if (!Barcodes.isValid(code)) {
            showResult(tab, Message(
                "Barcode format fout",
                "\"$code\" is geen leerlingnummer (minstens 6 cijfers)",
                true,
            ))
            return
        }
        val withIntroducee = introducee
        update(tab) { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val page = io {
                    if (tab == Tab.VERKOOP) client.verkoop(code, withIntroducee) else client.controle(code)
                }
                val msg = page.message
                    ?: Message("Onbekend", "Geen bevestiging van de server ontvangen voor $code, controleer de lijst", true)
                if (tab == Tab.VERKOOP && !msg.isError) introducee = false
                update(tab) { it.copy(page = page, submitting = false) }
                showResult(tab, msg)
            } catch (e: SessionExpiredException) {
                update(tab) { it.copy(submitting = false) }
                sessionExpired(e)
            } catch (e: Exception) {
                update(tab) { it.copy(submitting = false) }
                showResult(tab, Message("Mislukt", "$code: ${failMessage(e)}. Controleer of het is opgeslagen.", true))
            }
        }
    }

    fun delete(tab: Tab, link: String) {
        update(tab) { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                io { client.delete(link) }
                load(tab)
            } catch (e: SessionExpiredException) {
                update(tab) { it.copy(loading = false) }
                sessionExpired(e)
            } catch (e: Exception) {
                update(tab) { it.copy(loading = false, loadError = failMessage(e)) }
            }
        }
    }

    private fun showResult(tab: Tab, msg: Message) {
        update(tab) { it.copy(result = msg, resultSeq = it.resultSeq + 1, feedbackPending = true) }
    }

    fun feedbackDone(tab: Tab) {
        update(tab) { it.copy(feedbackPending = false) }
    }

    companion object {
        private const val SAME_CODE_IGNORE_MS = 4000L
    }
}
