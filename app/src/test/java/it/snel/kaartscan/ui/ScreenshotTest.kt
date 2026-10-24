package it.snel.kaartscan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import it.snel.kaartscan.Counts
import it.snel.kaartscan.Message
import it.snel.kaartscan.Entry
import it.snel.kaartscan.Me
import it.snel.kaartscan.Aantallen
import it.snel.kaartscan.ScreenState
import it.snel.kaartscan.ServerSnapshot
import it.snel.kaartscan.ServerStatus
import it.snel.kaartscan.Tab
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens with made-up data for the user documentation in
 * docs/. Only writes images when run through `./gradlew recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    private val user = "j.devries"
    private val me = Me(user, "Examengala 2021", introduceeToegestaan = true)

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("../docs/screenshots/$name.png") {
            KaartscanTheme { content() }
        }

    private fun entry(
        id: Int,
        tijdstip: String,
        barcode: String,
        gast: String,
        introducee: Boolean,
        deletedBy: String? = null,
        bron: String? = "scan",
    ) = Entry(
        id = id,
        barcode = barcode,
        gast = gast,
        introducee = introducee,
        tijdstip = tijdstip,
        door = user,
        bron = bron,
        verwijderd = deletedBy?.let { "verwijderd door $it op $tijdstip" },
        deleteLink = if (deletedBy == null) "https://example.org/kaartscan/do_delete.php?kaartverkoop_id=$id" else null,
    )

    private val verkoopRows = listOf(
        entry(41, "2021-04-16 19:42:10", "213874", "Sanne Bakker (6V2)", true),
        entry(40, "2021-04-16 19:41:55", "209331", "Daan Visser (5H1)", false),
        entry(39, "2021-04-16 19:40:02", "211408", "Lotte van Dijk (6V1)", false),
        entry(38, "2021-04-16 19:38:47", "210576", "Emma Smit (5H2)", true),
    )

    private val controleRows = listOf(
        entry(12, "2021-04-16 21:15:03", "213874", "Sanne Bakker (6V2)", true, bron = null),
        entry(11, "2021-04-16 21:14:40", "211408", "Lotte van Dijk (6V1)", false, bron = null),
    )

    private val aantallen = Aantallen(verkocht = Counts("143", "128", "15"), binnen = Counts("87", "79", "8"))

    @Composable
    private fun GreenDot() = ServerStatusDot(
        snapshot = ServerSnapshot(status = ServerStatus.OK),
        loggedIn = true,
        server = "https://srv-ovc-linux.ovc.nl/kaartscan/",
        user = user,
        feest = me.feestNaam,
    )

    @Composable
    private fun CameraIllustration() {
        // stand-in for the live camera image: a student card under the aiming line
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF3B3F45), Color(0xFF1E2024))))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cardW = size.width * 0.62f
                val cardH = size.height * 0.68f
                val left = (size.width - cardW) / 2
                val top = (size.height - cardH) / 2
                drawRoundRect(Color(0xFFF4F1EA), Offset(left, top), Size(cardW, cardH), CornerRadius(24f))
                drawRect(Color(0xFF1A3A6B), Offset(left, top), Size(cardW, cardH * 0.22f))
                val widths = listOf(3, 1, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1, 2, 1, 3, 1, 2, 2, 1, 3, 1)
                val unit = cardW * 0.8f / widths.sumOf { it + 1 }
                var x = left + cardW * 0.1f
                val barTop = top + cardH * 0.42f
                for (w in widths) {
                    drawRect(Color(0xFF111111), Offset(x, barTop), Size(w * unit, cardH * 0.4f))
                    x += (w + 1) * unit
                }
                drawRect(Color.Red.copy(alpha = 0.75f), Offset(size.width * 0.1f, size.height / 2 - 2f), Size(size.width * 0.8f, 5f))
            }
        }
    }

    @Test
    fun login() = shot("1-login") {
        LoginContent(
            username = user,
            onUsernameChange = {},
            password = "geheimwachtwoord",
            onPasswordChange = {},
            server = "https://srv-ovc-linux.ovc.nl/kaartscan/",
            onServerChange = {},
            editServer = false,
            onEditServer = {},
            error = null,
            busy = false,
            canLogin = true,
            onLogin = {},
            resumableUser = null,
            onResume = {},
            checkingSession = false,
            statusDot = {
                ServerStatusDot(snapshot = ServerSnapshot(), loggedIn = false, server = "", user = null, feest = null)
            },
        )
    }

    @Test
    fun start() = shot("2-start") {
        MainScaffold(null, me, onBack = {}, onRefresh = {}, statusDot = { GreenDot() }) {
            StartScreen(onSelect = {}, onLogout = {})
        }
    }

    @Test
    fun verkoop() = shot("3-verkoop") {
        MainScaffold(Tab.VERKOOP, me, onBack = {}, onRefresh = {}, statusDot = { GreenDot() }) {
            ScanContent(
                tab = Tab.VERKOOP,
                state = ScreenState(
                    entries = verkoopRows,
                    loaded = true,
                    result = Message("Success", "Barcode 213874 stored in database", false),
                ),
                introducee = false,
                introduceeToegestaan = true,
                onIntroduceeChange = {},
                manual = "",
                onManualChange = {},
                onSubmit = {},
                cameraOn = true,
                onToggleCamera = {},
                onDelete = {},
            ) { CameraIllustration() }
        }
    }

    @Test
    fun verkoopZonderIntroducee() = shot("3b-verkoop-zonder-introducee") {
        MainScaffold(Tab.VERKOOP, me.copy(feestNaam = "Kerstgala 2021", introduceeToegestaan = false), onBack = {}, onRefresh = {}, statusDot = { GreenDot() }) {
            ScanContent(
                tab = Tab.VERKOOP,
                state = ScreenState(
                    entries = verkoopRows.map { it.copy(introducee = false) },
                    loaded = true,
                    result = Message("Success", "Barcode 213874 stored in database", false),
                ),
                introducee = false,
                introduceeToegestaan = false,
                onIntroduceeChange = {},
                manual = "",
                onManualChange = {},
                onSubmit = {},
                cameraOn = true,
                onToggleCamera = {},
                onDelete = {},
            ) { CameraIllustration() }
        }
    }

    @Test
    fun controle() = shot("4-controle") {
        MainScaffold(Tab.CONTROLE, me, onBack = {}, onRefresh = {}, statusDot = { GreenDot() }) {
            ScanContent(
                tab = Tab.CONTROLE,
                state = ScreenState(
                    entries = controleRows,
                    aantallen = aantallen,
                    loaded = true,
                    result = Message(
                        "Success",
                        "Barcode 213874 is toegelaten op het feest en ingeschreven als aanwezig MET introducee.",
                        false,
                    ),
                ),
                introducee = false,
                introduceeToegestaan = true,
                onIntroduceeChange = {},
                manual = "",
                onManualChange = {},
                onSubmit = {},
                cameraOn = true,
                onToggleCamera = {},
                onDelete = {},
            ) { CameraIllustration() }
        }
    }

    @Test
    fun controleFout() = shot("5-controle-fout") {
        MainScaffold(Tab.CONTROLE, me, onBack = {}, onRefresh = {}, statusDot = { GreenDot() }) {
            ScanContent(
                tab = Tab.CONTROLE,
                state = ScreenState(
                    entries = controleRows.take(1),
                    aantallen = aantallen,
                    loaded = true,
                    result = Message("Dubbel", "Barcode 213874 is al binnen", true),
                ),
                introducee = false,
                introduceeToegestaan = true,
                onIntroduceeChange = {},
                manual = "",
                onManualChange = {},
                onSubmit = {},
                cameraOn = true,
                onToggleCamera = {},
                onDelete = {},
            ) { CameraIllustration() }
        }
    }

    @Test
    fun overzicht() = shot("6-overzicht") {
        MainScaffold(Tab.OVERZICHT, me, onBack = {}, onRefresh = {}, statusDot = { GreenDot() }) {
            OverzichtContent(
                state = ScreenState(
                    entries = verkoopRows + entry(37, "2021-04-16 19:30:12", "208120", "Milan de Boer (5H2)", false, deletedBy = user),
                    aantallen = aantallen,
                    loaded = true,
                ),
                alphabetic = false,
                onAlphabeticChange = {},
                showDeleted = true,
                onShowDeletedChange = {},
                query = "",
                onQueryChange = {},
                onDelete = {},
            )
        }
    }
}
