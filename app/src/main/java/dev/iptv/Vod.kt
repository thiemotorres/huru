package dev.iptv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import org.json.JSONObject
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as MediaPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay

/** Was abgespielt wird. resumeKey merkt die Position, following läuft danach automatisch (nächste Folgen). */
data class Playback(
    val url: String,
    val title: String,
    val resumeKey: String,
    val poster: String?,
    val following: List<Playback> = emptyList(),
)

data class Progress(val playback: Playback, val position: Long, val duration: Long, val updated: Long)

/** Angefangene Filme/Folgen ("movie-…", "ep-…") für Fortsetzen und "Weiterschauen". */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("continue", Context.MODE_PRIVATE)

    fun save(p: Playback, position: Long, duration: Long) {
        // Fast zu Ende oder kaum angefangen: nicht merken.
        if (duration > 0 && position > duration * 0.95 || position < 30_000) {
            prefs.edit().remove(p.resumeKey).apply()
            return
        }
        val json = JSONObject()
            .put("url", p.url).put("title", p.title).put("poster", p.poster ?: "")
            .put("pos", position).put("dur", duration).put("t", System.currentTimeMillis())
        prefs.edit().putString(p.resumeKey, json.toString()).apply()
    }

    fun position(key: String): Long = list(key).firstOrNull { it.playback.resumeKey == key }?.position ?: 0

    /** Neueste zuerst. */
    fun list(prefix: String): List<Progress> = prefs.all
        .filterKeys { it.startsWith(prefix) }
        .mapNotNull { (key, value) ->
            runCatching {
                val o = JSONObject(value as String)
                Progress(
                    Playback(o.getString("url"), o.getString("title"), key, o.optString("poster").ifBlank { null }),
                    o.getLong("pos"), o.getLong("dur"), o.getLong("t"),
                )
            }.getOrNull()
        }
        .sortedByDescending { it.updated }
}

private const val SEARCH = "__search"
private const val CONTINUE = "__continue"

/** Sperrt eine Fokusgruppe für D-Pad-Navigation, solange etwas darüber liegt. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.blockFocus(blocked: Boolean) = this
    .focusProperties { if (blocked) enter = { FocusRequester.Cancel } }
    .focusGroup()

@Composable
fun VodScreen(x: Xtream, series: Boolean, header: @Composable () -> Unit, autoFocus: Boolean) {
    val context = LocalContext.current
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var byCategory by remember { mutableStateOf<Map<String, List<VodItem>>>(emptyMap()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var playing by remember { mutableStateOf<Playback?>(null) }
    var lastPicked by remember { mutableStateOf<Int?>(null) } // Poster, auf das der Fokus zurückkehrt
    var query by remember { mutableStateOf("") }
    val store = remember { ProgressStore(context) }
    val prefix = if (series) "ep-" else "movie-"
    val pickedFocus = remember { FocusRequester() }
    val firstCategory = remember { FocusRequester() }

    LaunchedEffect(series) {
        val actions =
            if (series) listOf("get_series_categories", "get_series")
            else listOf("get_vod_categories", "get_vod_streams")
        runCatching {
            x.loadLists(context.cacheDir, actions) { (c, items) ->
                val parsed = if (series) x.parseSeries(items) else x.parseMovies(items)
                byCategory = parsed.groupBy { it.categoryId }
                categories = x.parseCategories(c).filter { byCategory[it.id] != null }
                if (selected == null) selected = if (store.list(prefix).isNotEmpty()) CONTINUE else categories?.firstOrNull()?.id
            }
        }.onFailure { if (categories == null) error = it.message ?: "Laden fehlgeschlagen" }
    }

    val loaded = categories
    if (loaded == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            header()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: if (series) "Lade Serien…" else "Lade Filme…")
            }
        }
        return
    }
    LaunchedEffect(Unit) { if (autoFocus) { awaitFrame(); runCatching { firstCategory.requestFocus() } } }
    val inProgress = remember(playing) { store.list(prefix) } // nach dem Schauen neu lesen
    val cats = buildList {
        add(Category(SEARCH, "Suchen"))
        if (inProgress.isNotEmpty()) add(Category(CONTINUE, "▶ Weiterschauen"))
        addAll(loaded)
    }
    val all = remember(byCategory) { byCategory.values.flatten().map { it to searchKey(it.name) } }
    val items = when (selected) {
        SEARCH -> remember(query, all) {
            val q = searchKey(query.trim())
            if (q.length < 2) emptyList() else all.filter { q in it.second }.map { it.first }.take(300)
        }
        else -> byCategory[selected].orEmpty()
    }
    fun open(item: VodItem) {
        lastPicked = item.id
        if (series) detail = item
        else playing = Playback(x.movieUrl(item), item.name, "movie-${item.id}", item.poster)
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(selected) { grid.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(24.dp).blockFocus(detail != null || playing != null),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.width(260.dp)) {
                header()
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(cats, key = { it.id }) { cat ->
                        CategoryItem(
                            cat,
                            selected = cat.id == selected,
                            modifier = Modifier
                                .then(if (cat.id == selected) Modifier.focusRequester(firstCategory) else Modifier)
                                .onFocusChanged { if (it.isFocused) selected = cat.id },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
            if (selected == SEARCH) {
                Field("Titel suchen (mind. 2 Zeichen)", query, imeAction = ImeAction.Search) { query = it }
                Spacer(Modifier.height(12.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                state = grid,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                fun focusMod(id: Int) = if (id == lastPicked) Modifier.focusRequester(pickedFocus) else Modifier
                if (selected == CONTINUE) {
                    items(inProgress, key = { it.playback.resumeKey }) { p ->
                        val id = p.playback.resumeKey.hashCode()
                        Poster(p.playback.title, p.playback.poster, focusMod(id), progress = if (p.duration > 0) p.position.toFloat() / p.duration else null) {
                            lastPicked = id
                            playing = p.playback
                        }
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        Poster(item.name, item.poster, focusMod(item.id)) { open(item) }
                    }
                }
            }
            }
        }
        detail?.let { s ->
            SeriesDetail(
                x, s,
                blocked = playing != null,
                onPlay = { ep, episodes ->
                    fun pb(e: Episode) = Playback(x.episodeUrl(e), "${s.name} · S${e.season} E${e.num}", "ep-${e.id}", s.poster)
                    playing = pb(ep).copy(following = episodes.drop(episodes.indexOf(ep) + 1).map(::pb))
                },
                onBack = { detail = null },
            )
        }
        playing?.let { p ->
            key(p.resumeKey) { // neue Folge → neuer Player
                VodPlayer(
                    p,
                    onEnded = { playing = p.following.firstOrNull()?.copy(following = p.following.drop(1)) },
                    onExit = { playing = null },
                )
            }
        }
    }
    // Fokus zurück auf das zuletzt gewählte Poster, wenn Detail/Player geschlossen werden.
    LaunchedEffect(detail == null && playing == null) {
        if (detail == null && playing == null && lastPicked != null) { awaitFrame(); runCatching { pickedFocus.requestFocus() } }
    }
}

@Composable
fun CategoryItem(
    cat: Category,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            Row(
                Modifier.alpha(if (dim) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cat.prefix?.let { Badge(it) }
                Text(cat.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun Poster(title: String, poster: String?, modifier: Modifier, progress: Float? = null, onClick: () -> Unit) {
    // Abstand > Vergrößerung beim Fokus, sonst überdeckt das Poster den Titel.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Accent), shape = RoundedCornerShape(8.dp)),
            ),
        ) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                Text(title, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, maxLines = 4)
                if (poster != null) {
                    AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (progress != null) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha = 0.6f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
                    }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@OptIn(ExperimentalTvMaterial3Api::class) // FilterChip
private fun SeriesDetail(
    x: Xtream,
    series: VodItem,
    blocked: Boolean,
    onPlay: (Episode, List<Episode>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }
    val firstEpisode = remember { FocusRequester() }
    var lastEpisode by remember { mutableStateOf<String?>(null) }
    val lastEpisodeFocus = remember { FocusRequester() }

    LaunchedEffect(series.id) {
        runCatching { x.episodes(series) }
            .onSuccess { episodes = it; season = it.firstOrNull()?.season }
            .onFailure { error = it.message ?: "Laden fehlgeschlagen" }
    }
    LaunchedEffect(episodes != null) {
        if (episodes != null) { awaitFrame(); runCatching { firstEpisode.requestFocus() } }
    }
    LaunchedEffect(blocked) { // Player zu → zurück auf die Folge
        if (!blocked && lastEpisode != null) { awaitFrame(); runCatching { lastEpisodeFocus.requestFocus() } }
    }
    BackHandler(enabled = !blocked, onBack = onBack)

    val positions = remember(blocked) { ProgressStore(context).list("ep-").associate { it.playback.resumeKey to it.position } }
    val seasons = episodes.orEmpty().map { it.season }.distinct()

    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(48.dp)
            .blockFocus(blocked),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(series.poster, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Crop)
            Text(series.name, style = MaterialTheme.typography.titleLarge)
            series.plot?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.7f))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                error != null -> Text(error!!)
                episodes == null -> Text("Lade Folgen…")
                episodes!!.isEmpty() -> Text("Keine Folgen gefunden")
            }
            if (seasons.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s ->
                        FilterChip(
                            selected = s == season,
                            onClick = { season = s },
                            modifier = Modifier.onFocusChanged { if (it.isFocused) season = s },
                        ) { Text("Staffel $s") }
                    }
                }
            }
            val shown = episodes.orEmpty().filter { it.season == season }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(shown, key = { it.id }) { ep ->
                    val at = positions["ep-${ep.id}"] ?: 0
                    val (title, tags) = remember(ep) { cleanEpisodeTitle(ep.title, series.name) }
                    ListItem(
                        selected = false,
                        onClick = { lastEpisode = ep.id; onPlay(ep, episodes.orEmpty()) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    if (title.isEmpty()) "Folge ${ep.num}" else "${ep.num}. $title",
                                    Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                tags?.let { Badge(it) }
                            }
                        },
                        supportingContent = ep.plot?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        trailingContent = if (at > 0) { { Text("bei ${duration(at)}", color = Accent) } } else null,
                        modifier = when {
                            ep.id == lastEpisode -> Modifier.focusRequester(lastEpisodeFocus)
                            ep == shown.first() -> Modifier.focusRequester(firstEpisode)
                            else -> Modifier
                        },
                    )
                }
            }
        }
    }
}

/** Player für Filme und Folgen: OK pausiert, Links/Rechts spult, merkt sich die Position. */
@Composable
fun VodPlayer(playback: Playback, onEnded: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ProgressStore(context) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() } // offene Such-Tastatur würde sonst über dem Video wieder aufgehen
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }

    lateinit var player: ExoPlayer
    fun save() = store.save(playback, player.currentPosition, player.duration)
    player = remember {
        newPlayer(context).build().apply {
            setMediaItem(MediaItem.fromUri(playback.url))
            seekTo(store.position(playback.resumeKey))
            prepare()
            playWhenReady = true
            addListener(object : MediaPlayer.Listener {
                override fun onPlayerError(e: PlaybackException) { error = describe(e) }
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == MediaPlayer.STATE_BUFFERING
                    if (state == MediaPlayer.STATE_ENDED) { save(); onEnded() }
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { save(); player.release() } }
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            position = player.currentPosition
            length = player.duration.coerceAtLeast(0)
            if (++ticks % 20 == 0) save() // alle 10 s, falls die App hart beendet wird
            delay(500)
        }
    }
    LaunchedEffect(overlayTick) { overlay = true; delay(4_000); overlay = false }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onExit)

    fun seekBy(ms: Long) {
        player.seekTo((player.currentPosition + ms).coerceIn(0, length.takeIf { it > 0 } ?: Long.MAX_VALUE))
        position = player.currentPosition
        overlayTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (error != null) { error = null; player.prepare() }
                        player.playWhenReady = !player.playWhenReady
                        paused = !player.playWhenReady
                        overlayTick++
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> { seekBy(-10_000); true }
                    Key.DirectionRight, Key.MediaFastForward -> { seekBy(30_000); true }
                    Key.DirectionUp, Key.DirectionDown -> { overlayTick++; true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        VideoSurface(player)
        if (buffering && error == null) {
            Text("Lädt…", Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f))
        }
        AnimatedVisibility(
            visible = overlay || paused || error != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text((if (paused) "❚❚  " else "") + playback.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                error?.let { Text("Fehler: $it · OK zum Neuladen", color = MaterialTheme.colorScheme.error) }
                val progress = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Accent))
                }
                Text(
                    "${duration(position)} / ${duration(length)}   ·   ◀ 10 s   ▶ 30 s",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** 3725000 → "1:02:05", 65000 → "1:05". */
fun duration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
