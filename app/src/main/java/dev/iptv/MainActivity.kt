package dev.iptv

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("account", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                Surface(Modifier.fillMaxSize(), shape = RectangleShape) { App(prefs) }
            }
        }
    }
}

val Accent = Color(0xFFE8A33D)

// ponytail: Zugangsdaten im Klartext in SharedPreferences (app-privat). Reicht für den eigenen Stick.
private fun SharedPreferences.account() =
    getString("server", null)?.let { Account(it, getString("user", "")!!, getString("pass", "")!!) }

private fun SharedPreferences.save(a: Account?) = edit().apply {
    if (a == null) clear() else putString("server", a.server).putString("user", a.user).putString("pass", a.pass)
}.apply()

@Composable
fun App(prefs: SharedPreferences) {
    var xtream by remember { mutableStateOf(prefs.account()?.let(::Xtream)) }
    var mode by remember { mutableIntStateOf(0) } // 0 TV, 1 Filme, 2 Serien
    var switched by remember { mutableStateOf(false) } // nach einem Wechsel Fokus auf den Umschalter statt Liste
    var autoplay by remember { mutableStateOf(true) } // letzten Sender nur beim App-Start automatisch abspielen
    val header = @Composable { ModeSwitch(mode, onMode = { mode = it; switched = true }, focus = switched) }
    when (val x = xtream) {
        null -> LoginScreen { prefs.save(it.account); xtream = it }
        else -> when (mode) {
            0 -> BrowseScreen(
                x, header, autoFocus = !switched, autoplay = autoplay,
                onAutoplayed = { autoplay = false },
                onLogout = { prefs.save(null); xtream = null },
            )
            else -> VodScreen(x, series = mode == 2, header, autoFocus = !switched)
        }
    }
}

@Composable
fun ModeSwitch(mode: Int, onMode: (Int) -> Unit, focus: Boolean) {
    val requester = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("TV", "Filme", "Serien").forEachIndexed { i, label ->
            Surface(
                onClick = { if (i != mode) onMode(i) },
                modifier = if (i == mode) Modifier.focusRequester(requester) else Modifier,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (i == mode) Accent.copy(alpha = 0.25f) else Color.Transparent,
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    LaunchedEffect(Unit) { if (focus) { awaitFrame(); runCatching { requester.requestFocus() } } }
}

@Composable
fun LoginScreen(onLogin: (Xtream) -> Unit) {
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Xtream-Zugang", style = MaterialTheme.typography.headlineMedium)
        Field("Server (z. B. http://host:8080)", server, KeyboardType.Uri) { server = it }
        Field("Benutzer", user) { user = it }
        Field("Passwort", pass, KeyboardType.Password, password = true) { pass = it }
        Button(enabled = !busy && server.isNotBlank(), onClick = {
            busy = true; error = null
            val x = Xtream(Account(server, user, pass))
            scope.launch {
                runCatching { x.login() }
                    .onSuccess {
                        // Sonst bleibt die IME-Sitzung offen und Fire OS zeigt die Tastatur über dem Player wieder an.
                        keyboard?.hide()
                        focusManager.clearFocus(force = true)
                        onLogin(x)
                    }
                    .onFailure { error = it.message ?: "Verbindung fehlgeschlagen"; busy = false }
            }
        }) { Text(if (busy) "Verbinde…" else "Anmelden") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.width(480.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = imeAction),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .border(2.dp, if (focused) Accent else Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
    }
}

@Composable
fun BrowseScreen(
    x: Xtream,
    header: @Composable () -> Unit,
    autoFocus: Boolean,
    autoplay: Boolean,
    onAutoplayed: () -> Unit,
    onLogout: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var all by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var byCategory by remember { mutableStateOf<Map<String, List<Channel>>>(emptyMap()) }
    val context = LocalContext.current
    val favPrefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    val lastPrefs = context.getSharedPreferences("last", Context.MODE_PRIVATE)
    var favorites by remember { mutableStateOf(favPrefs.getStringSet("ids", emptySet())!!.toSet()) }
    fun toggleFavorite(ch: Channel) {
        val id = ch.id.toString()
        favorites = if (id in favorites) favorites - id else favorites + id
        favPrefs.edit().putStringSet("ids", favorites).apply()
    }
    val catPrefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)
    var hidden by remember { mutableStateOf(catPrefs.getStringSet("hidden", emptySet())!!.toSet()) }
    var showHidden by remember { mutableStateOf(false) }
    fun toggleHidden(cat: Category) {
        hidden = if (cat.id in hidden) hidden - cat.id else hidden + cat.id
        catPrefs.edit().putStringSet("hidden", hidden).apply()
    }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Int?>(null) } // Index in der aktuellen Kategorie
    var returnTo by remember { mutableStateOf<Int?>(null) }
    val epg = remember { mutableStateMapOf<Int, List<Programme>>() } // stream_id -> Sendungen, Cache für die Sitzung
    fun channelsOf(cat: String?) =
        if (cat == FAVORITES) all.filter { it.id.toString() in favorites } else byCategory[cat].orEmpty()

    LaunchedEffect(x) {
        var started = false
        runCatching {
            // Erst Cache, dann Server.
            x.loadLists(context.cacheDir, listOf("get_live_categories", "get_live_streams")) { (catJson, chanJson) ->
                val (cats, chans) = withContext(Dispatchers.Default) { x.parseCategories(catJson) to x.parseChannels(chanJson) }
                all = chans
                byCategory = chans.groupBy { it.categoryId }
                categories = cats.filter { byCategory[it.id] != null }
                if (started) return@loadLists
                started = true
                if (autoplay) {
                    onAutoplayed()
                    // Direkt mit dem zuletzt gesehenen Sender starten, falls es ihn noch gibt.
                    val lastCategory = lastPrefs.getString("category", null)
                    val last = channelsOf(lastCategory).indexOfFirst { it.id == lastPrefs.getInt("channel", -1) }
                    if (last >= 0) {
                        selected = lastCategory
                        playing = last
                        return@loadLists
                    }
                }
                selected = if (favorites.isNotEmpty()) FAVORITES else categories?.firstOrNull()?.id
            }
        }.onFailure { if (!started) error = it.message ?: "Laden fehlgeschlagen" }
    }

    val loaded = categories
    if (loaded == null) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(error ?: "Lade Sender…")
            if (error != null) Button(onClick = onLogout, Modifier.padding(top = 16.dp)) { Text("Abmelden") }
        }
        return
    }
    val hiddenCount = loaded.count { it.id in hidden }
    val cats = buildList {
        if (favorites.isNotEmpty()) add(Category(FAVORITES, "★ Favoriten"))
        addAll(if (showHidden) loaded else loaded.filter { it.id !in hidden })
        if (hiddenCount > 0) add(Category(TOGGLE_HIDDEN, if (showHidden) "Ausgeblendete verbergen" else "$hiddenCount ausgeblendet"))
    }
    val channels = remember(selected, all, favorites) { channelsOf(selected) }
    val firstCategory = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val channelList = rememberLazyListState()

    LaunchedEffect(Unit) { awaitFrame(); if (autoFocus && playing == null) runCatching { firstCategory.requestFocus() } }
    LaunchedEffect(returnTo) {
        val i = returnTo ?: return@LaunchedEffect
        channelList.scrollToItem(i)
        awaitFrame()
        runCatching { channelFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(260.dp)) {
                header()
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(cats, key = { _, c -> c.id }) { i, cat ->
                        val special = cat.id == FAVORITES || cat.id == TOGGLE_HIDDEN
                        CategoryItem(
                            cat,
                            selected = cat.id == selected,
                            dim = cat.id in hidden || cat.id == TOGGLE_HIDDEN,
                            onClick = { if (cat.id == TOGGLE_HIDDEN) showHidden = !showHidden },
                            onLongClick = { if (!special) toggleHidden(cat) }, // Kategorie aus-/einblenden
                            modifier = Modifier
                                .then(if (i == 0) Modifier.focusRequester(firstCategory) else Modifier)
                                .onFocusChanged { if (it.isFocused && cat.id != TOGGLE_HIDDEN) selected = cat.id },
                        )
                    }
                }
            }
            val now = rememberNow()
            val start = guideStart(now)
            Column(Modifier.weight(1f)) {
                GuideHeader(start, now)
                LazyColumn(state = channelList, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                        LaunchedEffect(ch.id, start) {
                            val cached = epg[ch.id]
                            // Neu laden, wenn noch nichts da ist oder alles Gecachte vorbei ist.
                            if (cached == null || cached.isNotEmpty() && cached.last().end < start + GUIDE_WINDOW) {
                                delay(300) // schnelles Scrollen bricht ab, bevor angefragt wird
                                epg[ch.id] = runCatching { x.shortEpg(ch, limit = 10) }.getOrDefault(emptyList())
                            }
                        }
                        GuideRow(
                            channel = ch,
                            programmes = epg[ch.id],
                            start = start,
                            now = now,
                            favorite = ch.id.toString() in favorites,
                            onClick = { returnTo = null; playing = i },
                            onLongClick = { toggleFavorite(ch) },
                            modifier = if (i == returnTo) Modifier.focusRequester(channelFocus) else Modifier,
                        )
                    }
                }
            }
        }
        playing?.let { start ->
            Player(
                x, channels, start,
                onChannel = { lastPrefs.edit().putString("category", selected).putInt("channel", it.id).apply() },
                onExit = { last -> playing = null; returnTo = last },
            )
        }
    }
}

private const val FAVORITES = "__favorites"
private const val TOGGLE_HIDDEN = "__toggle_hidden"

/** Kleines umrandetes Kürzel, z. B. Länderpräfix oder Qualität. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.7f),
    )
}

@Composable
fun Logo(ch: Channel, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        if (ch.logo != null) AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text(ch.num.toString(), style = MaterialTheme.typography.labelSmall)
    }
}
