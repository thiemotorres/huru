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

/** Was gerade abgespielt wird: Stream-URL, Anzeigename und Schlüssel für die Fortsetzen-Position. */
private data class Playback(val url: String, val title: String, val resumeKey: String)

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
                if (selected == null) selected = categories?.firstOrNull()?.id
            }
        }.onFailure { if (categories == null) error = it.message ?: "Laden fehlgeschlagen" }
    }

    val cats = categories
    if (cats == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            header()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: if (series) "Lade Serien…" else "Lade Filme…")
            }
        }
        return
    }
    LaunchedEffect(Unit) { if (autoFocus) { awaitFrame(); runCatching { firstCategory.requestFocus() } } }
    val items = byCategory[selected].orEmpty()
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
                                .then(if (cat == cats.first()) Modifier.focusRequester(firstCategory) else Modifier)
                                .onFocusChanged { if (it.isFocused) selected = cat.id },
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                state = grid,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Poster(item, if (item.id == lastPicked) Modifier.focusRequester(pickedFocus) else Modifier) {
                        lastPicked = item.id
                        if (series) detail = item
                        else playing = Playback(x.movieUrl(item), item.name, "movie-${item.id}")
                    }
                }
            }
        }
        detail?.let { s ->
            SeriesDetail(
                x, s,
                blocked = playing != null,
                onPlay = { ep -> playing = Playback(x.episodeUrl(ep), "${s.name} · S${ep.season} E${ep.num}", "ep-${ep.id}") },
                onBack = { detail = null },
            )
        }
        playing?.let { p ->
            VodPlayer(p.url, p.title, p.resumeKey, onExit = { playing = null })
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
private fun Poster(item: VodItem, modifier: Modifier, onClick: () -> Unit) {
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
                Text(item.name, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, maxLines = 4)
                if (item.poster != null) {
                    AsyncImage(item.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@OptIn(ExperimentalTvMaterial3Api::class) // FilterChip
private fun SeriesDetail(x: Xtream, series: VodItem, blocked: Boolean, onPlay: (Episode) -> Unit, onBack: () -> Unit) {
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

    val resume = remember(blocked) { context.getSharedPreferences("resume", Context.MODE_PRIVATE) }
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
                    val at = resume.getLong("ep-${ep.id}", 0)
                    ListItem(
                        selected = false,
                        onClick = { lastEpisode = ep.id; onPlay(ep) },
                        headlineContent = { Text("${ep.num}. ${ep.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
fun VodPlayer(url: String, title: String, resumeKey: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val resume = remember { context.getSharedPreferences("resume", Context.MODE_PRIVATE) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }

    val player = remember {
        newPlayer(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            seekTo(resume.getLong(resumeKey, 0))
            prepare()
            playWhenReady = true
            addListener(object : MediaPlayer.Listener {
                override fun onPlayerError(e: PlaybackException) { error = describe(e) }
                override fun onPlaybackStateChanged(state: Int) { buffering = state == MediaPlayer.STATE_BUFFERING }
            })
        }
    }
    fun save() {
        val p = player.currentPosition
        val d = player.duration
        resume.edit().apply {
            // Fast zu Ende oder kaum angefangen: nichts merken.
            if (d > 0 && p > d * 0.95 || p < 30_000) remove(resumeKey) else putLong(resumeKey, p)
        }.apply()
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
                Text((if (paused) "❚❚  " else "") + title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
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
