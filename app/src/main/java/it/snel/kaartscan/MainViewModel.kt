package it.snel.kaartscan

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class Tab { VERKOOP, OVERZICHT, CONTROLE }

data class ScreenState(
    val entries: List<Entry> = emptyList(),
    /** CONTROLE shows binnen, OVERZICHT shows verkocht */
    val aantallen: Aantallen? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val result: Message? = null,
    /** increases with every new result, used to trigger sound/vibration once */
    val resultSeq: Int = 0,
    val feedbackPending: Boolean = false,
    val loadError: String? = null,
)

private data class Loaded(val me: Me, val entries: List<Entry>, val aantallen: Aantallen?)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val client = KaartscanClient(app)

    /** Logged in user and current party; null: show the login screen. */
    var me by mutableStateOf<Me?>(null)
        private set
    /** At startup, checking whether the stored session is still valid. */
    var checkingSession by mutableStateOf(false)
        private set
    private var sessionJob: Job? = null
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

    /** for the status dot; grey (UNKNOWN) is decided by the UI when not logged in */
    val server: StateFlow<ServerSnapshot> get() = client.stats.snapshot
    var checking by mutableStateOf(false)
        private set

    val baseUrl: String get() = client.baseUrl
    val lastUsername: String get() = client.lastUsername

    private val screens: Map<Tab, MutableState<ScreenState>> =
        Tab.entries.associateWith { mutableStateOf(ScreenState()) }

    private var lastScan: String? = null
    private var lastScanAt = 0L

    init {
        // a still valid stored session skips the login screen
        if (client.hasSession()) {
            checkingSession = true
            sessionJob = viewModelScope.launch {
                try {
                    val user = io { client.checkSession() }
                    if (user != null) {
                        resetScreens()
                        me = user
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // no connection or a server error: the login screen, the dot shows it
                } finally {
                    checkingSession = false
                }
            }
        }
    }

    fun state(tab: Tab): ScreenState = screens.getValue(tab).value

    private var pinging = false

    /** Without a session (login screen): only check whether the server is alive, for the dot. */
    fun pingServer() {
        if (me != null || pinging) return
        pinging = true
        viewModelScope.launch {
            try {
                io { client.ping() }
            } finally {
                pinging = false
            }
        }
    }

    /**
     * Asks the server who we are, so the status dot also turns red when the
     * connection drops while nobody is scanning; refreshes the party too.
     * With [onlyIfStale], skipped when there was contact recently.
     */
    fun heartbeat(onlyIfStale: Boolean = true) {
        if (me == null || checking) return
        val last = client.stats.snapshot.value.lastContactAt
        if (onlyIfStale && last != null && System.currentTimeMillis() - last < HEARTBEAT_MS) return
        checking = true
        viewModelScope.launch {
            try {
                val user = io { client.me() }
                if (me != null) {
                    me = user
                    if (!user.introduceeToegestaan) introducee = false
                }
            } catch (e: SessionExpiredException) {
                if (me != null) sessionExpired(e)
            } catch (e: Exception) {
                // the status dot shows it
            } finally {
                checking = false
            }
        }
    }

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
                me = user
            } catch (e: LoginFailedException) {
                loginError = e.message
            } catch (e: Exception) {
                loginError = failMessage(e)
            } finally {
                loginBusy = false
            }
        }
    }

    /** "Naar inloggen" on the startup screen, when the session check takes long. */
    fun skipSessionCheck() {
        sessionJob?.cancel()
        sessionJob = null
        checkingSession = false
    }

    fun logout() {
        me = null
        loginError = null
        resetScreens()
        viewModelScope.launch { io { client.logout() } }
    }

    private fun sessionExpired(e: SessionExpiredException) {
        me = null
        loginError = "Sessie verlopen of geen toegang" + (e.message?.let { " ($it)" } ?: "") + ". Log opnieuw in."
    }

    private fun resetScreens() {
        for (s in screens.values) s.value = ScreenState()
        introducee = false
        lastScan = null
    }

    private fun failMessage(e: Exception): String = when (e) {
        is ServerException, is ApiException -> e.message ?: "Serverfout"
        is IOException -> "Netwerkfout: ${e.message ?: e.javaClass.simpleName}"
        else -> "Fout: ${e.message ?: e.javaClass.simpleName}"
    }

    // ---- reading ---------------------------------------------------------------

    /** Also refreshes [me], so a change of party on the server shows up. */
    private fun fetch(tab: Tab): Loaded {
        val user = client.me()
        return when (tab) {
            // kaartverkoop.php shows the latest 10, kaartcontrole.php all of them
            Tab.VERKOOP -> Loaded(user, client.kaartjes(limit = 10), null)
            Tab.CONTROLE -> Loaded(user, client.controles(), client.aantallen())
            Tab.OVERZICHT -> Loaded(user, client.kaartjes(alphabetic, showDeleted), client.aantallen())
        }
    }

    private fun apply(tab: Tab, loaded: Loaded) {
        me = loaded.me
        if (!loaded.me.introduceeToegestaan) introducee = false
        update(tab) { it.copy(entries = loaded.entries, aantallen = loaded.aantallen, loaded = true, loading = false) }
    }

    fun load(tab: Tab) {
        if (me == null) return
        update(tab) { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                apply(tab, io { fetch(tab) })
            } catch (e: SessionExpiredException) {
                update(tab) { it.copy(loading = false) }
                sessionExpired(e)
            } catch (e: Exception) {
                update(tab) { it.copy(loading = false, loadError = failMessage(e)) }
            }
        }
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
            showResult(tab, Message("Barcode format fout", "\"$code\" is geen leerlingnummer (minstens 6 cijfers)", true))
            return
        }
        val withIntroducee = introducee && me?.introduceeToegestaan == true
        update(tab) { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val msg = io {
                    if (tab == Tab.VERKOOP) client.verkoop(code, withIntroducee) else client.controle(code)
                } ?: Message("Onbekend", "Geen bevestiging van de server ontvangen voor $code, controleer de lijst", true)
                if (tab == Tab.VERKOOP && !msg.isError) introducee = false
                update(tab) { it.copy(submitting = false) }
                showResult(tab, msg)
                load(tab)
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
        const val HEARTBEAT_MS = 30_000L
    }
}
