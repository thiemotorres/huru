package io.github.thiemotorres.huru

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

/**
 * Store/README mockups rendered on the JVM with fictional data (no real channels or titles).
 * Record: ./gradlew recordPaparazziDebug  → app/src/test/snapshots/images/
 */
class Mockups {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1920, screenHeight = 1080, xdpi = 320, ydpi = 320,
            density = Density.XHIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        maxPercentDifference = 0.1,
        useDeviceResolution = true, // full 1920×1080 instead of a 1000 px thumbnail
    )

    private fun bitmap(name: String) =
        BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mock/$name.png")!!)

    @Before
    fun images() {
        val engine = FakeImageLoaderEngine.Builder().apply {
            (0 until 20).forEach { intercept("mock:poster$it", BitmapDrawable(paparazzi.resources, bitmap("poster$it"))) }
        }.build()
        Coil.setImageLoader(
            ImageLoader.Builder(paparazzi.context).components { add(engine) }.dispatcher(Dispatchers.Unconfined).build()
        )
    }

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides false) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Night, background = Night)) {
                Surface(Modifier.fillMaxSize(), shape = RectangleShape) {
                    // Composing inside a lazy item lets Coil's synchronous fake images land in the single rendered frame.
                    LazyColumn(userScrollEnabled = false) { item { Box(Modifier.fillParentMaxSize()) { content() } } }
                }
            }
        }
    }

    // --- fictional data -------------------------------------------------------------------------------

    private val now = Calendar.getInstance().apply { set(2026, 10, 12, 20, 47, 0) }.timeInMillis
    private val start = guideStart(now)
    private fun at(minutes: Int) = start + minutes * 60_000L

    private val channels = listOf(
        "Northlight One HD", "Studio 1 FHD", "Harbor News HD", "Summit Sports FHD",
        "Cinema Gold HD", "Kids Planet", "Atlas Docs HD", "Jazz TV",
    ).mapIndexed { i, name -> Channel(i + 1, 101 + i, name, logo = null, categoryId = "1") }
    // ponytail: no logos – next to the category list, Coil's fake images miss Paparazzi's single frame;
    // the app shows the channel number instead, exactly as for real channels without a logo.

    private val schedules = listOf(
        listOf("Evening News" to 20, "The Long Summer" to 105),
        listOf("Quiz Night" to 45, "Late Show" to 60, "Night Film" to 50),
        listOf("World Report" to 30, "Business Today" to 30, "Weather" to 10, "Headlines" to 50),
        listOf("Champions League: Matchday 4" to 125, "Highlights" to 30),
        listOf("Northern Lights" to 95, "Paper Boats" to 70),
        listOf("Sea Explorers" to 25, "Robot Friends" to 25, "Goodnight Stories" to 70),
        listOf("Planet Oceans" to 55, "The Silk Road" to 55, "Volcanoes" to 40),
        listOf("Live from Montreux" to 90, "Late Jam" to 60),
    ).map { slots ->
        var t = -15
        slots.map { (title, len) -> Programme(title, at(t), at(t + len)).also { t += len } }
    }

    private val liveCategories = listOf(
        Category("fav", "★ Favorites"), Category("1", "UK | Entertainment"), Category("2", "UK | News"),
        Category("3", "UK | Sports"), Category("4", "US | Movies"), Category("5", "DE | Kids"),
        Category("6", "Documentaries"), Category("7", "Music"),
    )

    private val movies = listOf(
        "Northern Lights", "Paper Boats", "City of Glass", "Dune Sea", "Quiet Harbor", "Starfall",
        "The Long Summer", "Golden Hour", "Last Train West", "Wild Orchard", "Salt & Stone", "Midnight Pines",
        "Coastline", "The Lighthouse", "Ember Valley", "Blue Hour", "Moon Garden", "Far Shore",
    )

    // --- screens --------------------------------------------------------------------------------------

    @Test fun live_guide() = shot {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(260.dp)) {
                ModeSwitch(0, onMode = {}, focus = false)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    liveCategories.forEach { CategoryItem(it, selected = it.id == "1") }
                }
            }
            Column(Modifier.weight(1f)) {
                GuideHeader(start, now)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    channels.forEachIndexed { i, ch ->
                        GuideRow(ch, schedules[i], start, now, favorite = i in setOf(0, 3), onClick = {}, onLongClick = {})
                    }
                }
            }
        }
    }

    @Test fun player() = shot {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bitmap("still").asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomCenter)) { InfoBar(channels[4], schedules[4], error = null, now = now, previous = channels[2], reminded = true) }
        }
    }

    @Test fun player_zap() = shot {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bitmap("still2").asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomCenter)) { InfoBar(channels[3], schedules[3], error = null, now = now) }
        }
    }

    @Test fun movies() = shot {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(260.dp)) {
                ModeSwitch(1, onMode = {}, focus = false)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Search", "▶ Continue watching", "New releases", "Drama", "Comedy", "Family", "Documentary", "Classics")
                        .forEachIndexed { i, name -> CategoryItem(Category("$i", name), selected = i == 1) }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(movies) { i, title ->
                    Poster(title, "mock:poster$i", Modifier, progress = if (i < 6) listOf(.35f, .8f, .1f, .55f, .2f, .9f)[i] else null) {}
                }
            }
        }
    }

    @Test fun series_detail() = shot {
        val episodes = listOf("Pilot", "The Market", "Low Tide", "Old Friends", "The Storm", "Lanterns", "Homecoming", "Last Ferry")
        Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(bitmap("poster13").asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Text("Harbour Street", style = MaterialTheme.typography.titleLarge)
                Text(
                    "A small fishing town, a closed-down harbour and a family that refuses to leave. Season two picks up after the storm.",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                @OptIn(ExperimentalTvMaterial3Api::class)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { s -> FilterChip(selected = s == 2, onClick = {}) { Text("Season $s") } }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    episodes.forEachIndexed { i, title ->
                        ListItem(
                            selected = false, onClick = {},
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("${i + 1}. $title", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (i < 4) Badge("FHD")
                                }
                            },
                            trailingContent = if (i == 2) { { Text("at 12:40", color = Accent) } } else null,
                        )
                    }
                }
            }
        }
    }

    @Test fun settings() = shot {
        val sources = listOf(DEMO_SOURCE.copy(id = "demo"), Account("http://tv.example.com:8080", "anna", "x", id = "home", name = "Home"))
        SettingsScreen(header = { ModeSwitch(3, onMode = {}, focus = false) }, sources, sources[1], null, {}, {}, {}, {})
    }

    @Test fun setup() = shot { SourceForm(initial = null, onSave = {}, onCancel = null) }

    @Test fun loading_live() = shot { LiveSkeleton(header = { ModeSwitch(0, onMode = {}, focus = false) }) }

    @Test fun loading_movies() = shot { GridSkeleton(header = { ModeSwitch(1, onMode = {}, focus = false) }) }

    @Test fun splash() = shot { SplashFrame(rise = 1f, fly = 1f, flap = 1f, word = 1f) }
}
