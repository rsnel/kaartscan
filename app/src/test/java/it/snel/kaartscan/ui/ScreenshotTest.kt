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
import it.snel.kaartscan.Cell
import it.snel.kaartscan.Counts
import it.snel.kaartscan.Message
import it.snel.kaartscan.Page
import it.snel.kaartscan.Row
import it.snel.kaartscan.ScreenState
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

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("../docs/screenshots/$name.png") {
            KaartscanTheme { content() }
        }

    private fun row(
        tijdstip: String,
        barcode: String,
        gast: String,
        introducee: Boolean,
        id: Int,
        deletedBy: String? = null,
        verkoop: Boolean = true,
    ): Row {
        val cells = linkedMapOf(
            "tijdstip" to Cell(tijdstip, null),
            "doc" to Cell(user, null),
            "barcode" to Cell(barcode, null),
            "gast" to Cell(gast, null),
            "introducee" to Cell(if (introducee) "ja" else "nee", null),
            "status" to if (deletedBy != null) Cell("verwijderd door $deletedBy op $tijdstip", null)
            else Cell("verwijderen", "https://example.org/kaartscan/do_delete.php?kaartverkoop_id=$id"),
        )
        if (verkoop) cells["bron"] = Cell("scan", null)
        return Row(cells)
    }

    private fun page(
        heading: String?,
        rows: List<Row>,
        counts: Counts? = null,
        hint: String? = null,
    ) = Page(
        isLoginPage = false,
        heading = heading,
        notice = null,
        error = null,
        loggedInAs = user,
        counts = counts,
        columns = emptyList(),
        rows = rows,
        hint = hint,
    )

    private val verkoopRows = listOf(
        row("2026-10-31 19:42:10", "213874", "Sanne Bakker (6V2)", true, 41),
        row("2026-10-31 19:41:55", "209331", "Daan Visser (5H1)", false, 40),
        row("2026-10-31 19:40:02", "211408", "Lotte van Dijk (6V1)", false, 39),
        row("2026-10-31 19:38:47", "210576", "Emma Smit (5H2)", true, 38),
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
        )
    }

    @Test
    fun start() = shot("2-start") {
        MainScaffold(null, user, onBack = {}, onRefresh = {}) {
            StartScreen(onSelect = {}, onLogout = {})
        }
    }

    @Test
    fun verkoop() = shot("3-verkoop") {
        MainScaffold(Tab.VERKOOP, user, onBack = {}, onRefresh = {}) {
            ScanContent(
                tab = Tab.VERKOOP,
                state = ScreenState(
                    page = page(
                        "Kaartverkoop Halloweenfeest 2026",
                        verkoopRows,
                        hint = "De barcode moet een leerlingnummer zijn van minstens 6 cijfers.",
                    ),
                    result = Message("Success", "Barcode 213874 stored in database", false),
                ),
                introducee = false,
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
        MainScaffold(Tab.CONTROLE, user, onBack = {}, onRefresh = {}) {
            ScanContent(
                tab = Tab.CONTROLE,
                state = ScreenState(
                    page = page(
                        "Overzocht lln die binnen zijn",
                        listOf(
                            row("2026-10-31 21:15:03", "213874", "Sanne Bakker (6V2)", true, 12, verkoop = false),
                            row("2026-10-31 21:14:40", "211408", "Lotte van Dijk (6V1)", false, 11, verkoop = false),
                        ),
                        counts = Counts("87", "79", "8"),
                    ),
                    result = Message(
                        "Success",
                        "Barcode 213874 is toegelaten op het feest en ingeschreven als aanwezig MET introducee.",
                        false,
                    ),
                ),
                introducee = false,
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
        MainScaffold(Tab.CONTROLE, user, onBack = {}, onRefresh = {}) {
            ScanContent(
                tab = Tab.CONTROLE,
                state = ScreenState(
                    page = page(
                        "Overzocht lln die binnen zijn",
                        listOf(row("2026-10-31 21:15:03", "213874", "Sanne Bakker (6V2)", true, 12, verkoop = false)),
                        counts = Counts("87", "79", "8"),
                    ),
                    result = Message("Dubbel", "Barcode 213874 is al binnen", true),
                ),
                introducee = false,
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
        MainScaffold(Tab.OVERZICHT, user, onBack = {}, onRefresh = {}) {
            OverzichtContent(
                state = ScreenState(
                    page = page(
                        "Overzicht verkochte kaartjes",
                        verkoopRows + row("2026-10-31 19:30:12", "208120", "Milan de Boer (5H2)", false, 37, deletedBy = user),
                        counts = Counts("143", "128", "15"),
                    ),
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
