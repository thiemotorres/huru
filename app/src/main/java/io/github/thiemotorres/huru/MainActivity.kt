package io.github.thiemotorres.huru

import android.content.Context
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusDirection
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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Night, background = Night)) {
                var splash by remember { mutableStateOf(true) }
                Box {
                    Surface(Modifier.fillMaxSize(), shape = RectangleShape) { App() }
                    // Splash on top of the app; lists and the last channel already load underneath
                    androidx.compose.animation.AnimatedVisibility(splash, exit = androidx.compose.animation.fadeOut()) {
                        Splash { splash = false }
                    }
                }
            }
        }
    }
}

val Accent = Coral
val Night = Color(0xFF1E1520) // very dark plum app background

@Composable
fun App() {
    val context = LocalContext.current
    val store = remember { SourceStore(context) }
    var sources by remember { mutableStateOf(store.load()) }
    var activeId by remember { mutableStateOf(store.activeId()) }
    val active = sources.firstOrNull { it.id == activeId } ?: sources.firstOrNull()
    var mode by remember { mutableIntStateOf(0) } // 0 TV, 1 movies, 2 series, 3 settings
    var switched by remember { mutableStateOf(false) } // after switching modes, focus the switcher once instead of the list
    var settingsFocus by remember { mutableStateOf<String?>(null) } // source row (or ADD_SOURCE) to focus in settings
    var autoplay by remember { mutableStateOf(true) } // auto-play the last channel only on app start
    fun update(list: List<Account>, newActive: String?) {
        sources = list; activeId = newActive; store.save(list, newActive)
    }
    val header = @Composable { ModeSwitch(mode, onMode = { mode = it; switched = true }, focus = switched, onFocused = { switched = false }) }

    if (active == null) {
        // First run (or all sources deleted): set up a source or try the demo.
        SourceForm(initial = null, onSave = { update(listOf(it), it.id); mode = 0; switched = false }, onCancel = null)
        return
    }
    key(active) { // switching or editing the active source rebuilds everything for it
        val x = remember { Xtream(active) }
        when (mode) {
            0 -> BrowseScreen(
                x, header, autoFocus = !switched, autoplay = autoplay,
                onAutoplayed = { autoplay = false },
                onManageSources = { mode = 3; switched = true },
            )
            3 -> SettingsScreen(
                header, sources, active, focusId = settingsFocus, onFocusId = { settingsFocus = it },
                onActivate = { autoplay = false; update(sources, it.id); mode = 0; switched = false },
                onSave = { saved ->
                    val list = if (sources.any { it.id == saved.id }) sources.map { if (it.id == saved.id) saved else it } else sources + saved
                    if (sources.none { it.id == saved.id }) store.deleteData(saved) // fresh id: make sure nothing stale is left
                    update(list, activeId ?: saved.id)
                    settingsFocus = saved.id
                },
                onDelete = { gone ->
                    store.deleteData(gone)
                    val index = sources.indexOf(gone)
                    val list = sources - gone
                    settingsFocus = (list.getOrNull(index) ?: list.getOrNull(index - 1))?.id ?: ADD_SOURCE
                    update(list, if (gone == active) list.firstOrNull()?.id else active.id)
                },
            )
            // key: movies and series must not share state (selected category, continue-watching list, …)
            else -> key(mode) { VodScreen(x, series = mode == 2, header, autoFocus = !switched) }
        }
    }
}

@Composable
fun ModeSwitch(mode: Int, onMode: (Int) -> Unit, focus: Boolean, onFocused: () -> Unit = {}) {
    val requester = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.mode_tv, R.string.mode_movies, R.string.mode_series, R.string.mode_settings).map { stringResource(it) }.forEachIndexed { i, label ->
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
    // Only right after a mode switch – not every time the switcher is rebuilt (e.g. after closing a form).
    LaunchedEffect(Unit) { if (focus) { awaitFrame(); runCatching { requester.requestFocus() }; onFocused() } }
}

/**
 * Text input for the remote: a focusable box that shows the value; OK switches to editing and opens the keyboard.
 * (A plain text field would pop up the full-screen Fire TV keyboard as soon as D-pad focus touches it.)
 */
@Composable
fun Field(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val editor = remember { FocusRequester() }
    val idle = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var leaving by remember { mutableStateOf(false) } // focus moves on deliberately (Next / D-pad up/down)
    var refocus by remember { mutableIntStateOf(0) }
    val box = Modifier.fillMaxWidth().height(52.dp)
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.width(480.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { leaving = true; focusManager.moveFocus(FocusDirection.Down) },
                    onSearch = { keyboard?.hide() },
                ),
                modifier = box
                    .focusRequester(editor)
                    .onFocusChanged { // leave editing only after having had focus (it also reports "unfocused" on attach)
                        if (it.isFocused) focused = true
                        else if (focused) {
                            // Closing the keyboard with Back drops focus somewhere else: bring it back to this field.
                            focused = false; editing = false
                            if (!leaving) refocus++
                            leaving = false
                        }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) false
                        else when (e.key) {
                            Key.DirectionCenter, Key.Enter -> { keyboard?.show(); true } // reopen after Back
                            Key.DirectionUp, Key.DirectionDown -> { leaving = true; false }
                            else -> false
                        }
                    }
                    .border(2.dp, Accent, shape)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            LaunchedEffect(Unit) { editor.requestFocus(); keyboard?.show() }
        } else {
            LaunchedEffect(refocus) { if (refocus > 0) { awaitFrame(); runCatching { idle.requestFocus() } } }
            Surface(
                onClick = { editing = true },
                modifier = box.focusRequester(idle),
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    contentColor = Color.White, focusedContentColor = Color.White,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.DarkGray), shape = shape),
                    focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Accent), shape = shape),
                ),
            ) {
                Text(
                    if (password) "•".repeat(value.length) else value,
                    Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
                    fontSize = 18.sp, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun BrowseScreen(
    x: Xtream,
    header: @Composable () -> Unit,
    autoFocus: Boolean,
    autoplay: Boolean,
    onAutoplayed: () -> Unit,
    onManageSources: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var all by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var byCategory by remember { mutableStateOf<Map<String, List<Channel>>>(emptyMap()) }
    val context = LocalContext.current
    val favPrefs = context.sourcePrefs("favorites", x.account)
    val lastPrefs = context.sourcePrefs("last", x.account)
    var favorites by remember { mutableStateOf(favPrefs.getStringSet("ids", emptySet())!!.toSet()) }
    fun toggleFavorite(ch: Channel) {
        val id = ch.id.toString()
        favorites = if (id in favorites) favorites - id else favorites + id
        favPrefs.edit().putStringSet("ids", favorites).apply()
    }
    val catPrefs = context.sourcePrefs("categories", x.account)
    var hidden by remember { mutableStateOf(catPrefs.getStringSet("hidden", emptySet())!!.toSet()) }
    var showHidden by remember { mutableStateOf(false) }
    fun toggleHidden(cat: Category) {
        hidden = if (cat.id in hidden) hidden - cat.id else hidden + cat.id
        catPrefs.edit().putStringSet("hidden", hidden).apply()
    }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Int?>(null) } // index within the current category
    var returnTo by remember { mutableStateOf<Int?>(null) }
    val epg = remember { mutableStateMapOf<Int, List<Programme>>() } // stream_id -> programmes, cached for the session
    fun channelsOf(cat: String?) =
        if (cat == FAVORITES) all.filter { it.id.toString() in favorites } else byCategory[cat].orEmpty()
    val loadingFailed = stringResource(R.string.loading_failed)

    LaunchedEffect(x) {
        var started = false
        runCatching {
            // Cache first, then server.
            x.loadLists(context.cacheDir, listOf("get_live_categories", "get_live_streams")) { (catJson, chanJson) ->
                val (cats, chans) = withContext(Dispatchers.Default) { x.parseCategories(catJson) to x.parseChannels(chanJson) }
                all = chans
                byCategory = chans.groupBy { it.categoryId }
                categories = cats.filter { byCategory[it.id] != null }
                if (started) return@loadLists
                started = true
                if (autoplay) {
                    onAutoplayed()
                    // Start straight into the last watched channel if it still exists.
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
        }.onFailure { if (!started) error = it.message ?: loadingFailed }
    }

    val loaded = categories
    if (loaded == null) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(error ?: stringResource(R.string.loading_channels))
            if (error != null) Button(onClick = onManageSources, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.manage_sources)) }
        }
        return
    }
    val hiddenCount = loaded.count { it.id in hidden }
    val favoritesLabel = stringResource(R.string.favorites)
    val hiddenLabel = if (showHidden) stringResource(R.string.hide_hidden) else stringResource(R.string.hidden_count, hiddenCount)
    val cats = buildList {
        if (favorites.isNotEmpty()) add(Category(FAVORITES, favoritesLabel))
        addAll(if (showHidden) loaded else loaded.filter { it.id !in hidden })
        if (hiddenCount > 0) add(Category(TOGGLE_HIDDEN, hiddenLabel))
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
                            onLongClick = { if (!special) toggleHidden(cat) }, // hide/unhide category
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
                            // Reload when nothing is cached yet or everything cached has ended.
                            if (cached == null || cached.isNotEmpty() && cached.last().end < start + GUIDE_WINDOW) {
                                delay(300) // fast scrolling cancels before the request goes out
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

/** Small outlined tag, e.g. a country prefix or quality. */
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
