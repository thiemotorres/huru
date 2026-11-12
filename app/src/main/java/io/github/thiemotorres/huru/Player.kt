package io.github.thiemotorres.huru

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.tv.material3.ListItem
import kotlinx.coroutines.android.awaitFrame
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as MediaPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen live player. Up/Down (or CH+/CH−) zaps within the category, OK shows info, Back exits. */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // DefaultLoadControl
fun Player(x: Xtream, channels: List<Channel>, start: Int, onChannel: (Channel) -> Unit, onWatched: (Channel) -> Unit, onExit: (Int) -> Unit) {
    val context = LocalContext.current
    // Pin the list on start: a background refresh must not switch the channel mid-viewing.
    val channels = remember { channels }
    var index by remember { mutableIntStateOf(start) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var epg by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val channel = channels[index]

    val player = remember {
        // Live TV: start after 1 s of buffer instead of 2.5 s (faster start and zapping).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
            .build()
        newPlayer(context).setLoadControl(loadControl).build().apply {
            playWhenReady = true
            addListener(object : MediaPlayer.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    // Some servers only deliver HLS: retry once as .m3u8, then stay on HLS.
                    if (x.liveExt == "ts") { x.liveExt = "m3u8"; retry++ } else error = describe(e)
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun play() {
        error = null
        player.setMediaItem(MediaItem.fromUri(x.streamUrl(channel)))
        player.prepare()
    }

    LaunchedEffect(retry) { if (retry > 0) play() }
    LaunchedEffect(channel) {
        paused = false // zapping always plays the new channel
        player.playWhenReady = true
        play()
        onChannel(channel)
        overlayTick++
        epg = emptyList()
        epg = runCatching { x.shortEpg(channel) }.getOrDefault(emptyList())
        delay(10_000) // only channels you stay on count as watched, not every zap
        onWatched(channel)
    }
    LaunchedEffect(overlayTick) { overlay = true; delay(4_000); overlay = false }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide(); focus.requestFocus() }
    BackHandler { onExit(index) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || menu) return@onKeyEvent false
                when (e.key) {
                    Key.Menu -> { menu = true; true }
                    Key.DirectionUp, Key.ChannelUp -> { index = (index - 1 + channels.size) % channels.size; true }
                    Key.DirectionDown, Key.ChannelDown -> { index = (index + 1) % channels.size; true }
                    Key.DirectionCenter, Key.Enter -> { if (error != null) play(); overlayTick++; true }
                    // Remote Play/Pause (required for Fire TV media apps); resuming jumps back to the live edge.
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        paused = !paused
                        if (!paused) player.seekToDefaultPosition()
                        player.playWhenReady = !paused
                        overlayTick++
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> true // don't let focus escape into the list behind
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        VideoSurface(player)
        AnimatedVisibility(
            visible = overlay || paused || error != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoBar(channel, epg, error, paused = paused)
        }
        if (menu) TrackMenu(player, { menu = false; focus.requestFocus() }, Modifier.align(Alignment.CenterEnd))
    }
}

/** ExoPlayer that also follows http→https redirects (movies/episodes often live on a CDN). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun newPlayer(context: android.content.Context): ExoPlayer.Builder {
    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        // No keep-alive: some catch-up servers leave stream bytes on an aborted connection, and the next request
        // (ExoPlayer jumps to the end of a .ts file to find its length) then reads them as "Unexpected status line".
        .setDefaultRequestProperties(mapOf("Connection" to "close"))
    // Preferred audio/subtitle language from the track menu, for every player and channel.
    val prefs = trackPrefs(context)
    val selector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredAudioLanguage(prefs.getString("audio", null))
                .setPreferredTextLanguage(prefs.getString("text", null))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, prefs.getBoolean("textOff", false)),
        )
    }
    return ExoPlayer.Builder(context)
        .setTrackSelector(selector)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
}

/** Device-wide (not per source): language is a viewer preference, not a provider one. */
private fun trackPrefs(context: android.content.Context) = context.getSharedPreferences("tracks", android.content.Context.MODE_PRIVATE)

/** Readable error: the HTTP status if the server sent one. */
fun describe(e: PlaybackException): String =
    (e.cause as? HttpDataSource.InvalidResponseCodeException)?.let { "HTTP ${it.responseCode}" } ?: e.errorCodeName

@Composable
fun VideoSurface(player: ExoPlayer) {
    // Subtitles are drawn by hand: the TextureView has no subtitle layer.
    var cues by remember { mutableStateOf("") }
    DisposableEffect(player) {
        val listener = object : MediaPlayer.Listener {
            override fun onCues(group: CueGroup) { cues = group.cues.mapNotNull { it.text }.joinToString("\n") }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            // TextureView instead of PlayerView/SurfaceView: the SurfaceView ends up behind the Compose layer (audio, no picture).
            factory = { TextureView(it).apply { isFocusable = false; keepScreenOn = true; player.setVideoTextureView(this) } },
            modifier = Modifier.fillMaxSize(),
        )
        if (cues.isNotBlank()) Text(
            cues,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp, start = 96.dp, end = 96.dp)
                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
        )
    }
}

/** Audio and subtitle choice (Menu key), shared by the live and VOD player. Traps focus until closed. */
@Composable
@OptIn(ExperimentalComposeUiApi::class) // focusProperties exit
fun TrackMenu(player: ExoPlayer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : MediaPlayer.Listener {
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }

    val prefs = trackPrefs(LocalContext.current)
    fun select(group: Tracks.Group?, i: Int, type: Int) {
        val language = group?.getTrackFormat(i)?.language?.takeIf { it != "und" }
        val audio = type == C.TRACK_TYPE_AUDIO
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().run {
            if (group == null) setTrackTypeDisabled(type, true)
            else setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).setTrackTypeDisabled(type, false)
            // The override only fits this stream; the language carries over to the next channel/episode.
            if (language != null) { if (audio) setPreferredAudioLanguage(language) else setPreferredTextLanguage(language) }
            this
        }.build()
        prefs.edit().apply {
            if (language != null) putString(if (audio) "audio" else "text", language)
            if (!audio) putBoolean("textOff", group == null)
        }.apply()
    }
    fun options(type: Int) = tracks.groups.filter { it.type == type }
        .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
    val audio = options(C.TRACK_TYPE_AUDIO)
    val text = options(C.TRACK_TYPE_TEXT)
    val audioLabels = trackLabels(audio)
    val textLabels = trackLabels(text)

    @Composable
    fun Option(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = ListItem(
        selected = selected, onClick = onClick, modifier = modifier,
        headlineContent = { Text(label) },
        trailingContent = if (selected) { { Text("✓") } } else null,
    )

    LazyColumn(
        modifier
            .fillMaxHeight().width(420.dp)
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(32.dp)
            // Compose turns Back into a focus exit, which the trap below cancels – so close here.
            .onPreviewKeyEvent { e -> if (e.key == Key.Back && e.type == KeyEventType.KeyDown) { onClose(); true } else false }
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleLarge, color = Color.White) }
        itemsIndexed(audio) { n, (g, i) ->
            Option(audioLabels[n], g.isTrackSelected(i), { select(g, i, C.TRACK_TYPE_AUDIO) }, if (n == 0) Modifier.focusRequester(first) else Modifier)
        }
        item { Text(stringResource(R.string.subtitles), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(top = 16.dp)) }
        item {
            Option(
                stringResource(R.string.subtitles_off), text.none { (g, i) -> g.isTrackSelected(i) }, { select(null, 0, C.TRACK_TYPE_TEXT) },
                if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier,
            )
        }
        itemsIndexed(text) { n, (g, i) -> Option(textLabels[n], g.isTrackSelected(i), { select(g, i, C.TRACK_TYPE_TEXT) }) }
    }
}

/** "English", "English 5.1", "English CC", … – numbered where names still collide. */
@Composable
private fun trackLabels(options: List<Pair<Tracks.Group, Int>>): List<String> {
    val forced = stringResource(R.string.track_forced)
    val names = options.mapIndexed { n, (g, i) ->
        val f = g.getTrackFormat(i)
        val name = f.label ?: f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage }
            ?: stringResource(R.string.track_n, n + 1)
        listOfNotNull(
            name,
            f.channelCount.takeIf { it > 2 }?.let { "${it - 1}.1" },
            "CC".takeIf { f.sampleMimeType?.contains("cea") == true },
            forced.takeIf { f.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
        ).joinToString(" ")
    }
    return names.mapIndexed { n, name ->
        if (names.count { it == name } > 1) "$name (${names.take(n + 1).count { it == name }})" else name
    }
}

val hhmm = SimpleDateFormat("HH:mm", Locale.GERMANY)

@Composable
internal fun InfoBar(channel: Channel, epg: List<Programme>, error: String?, now: Long = System.currentTimeMillis(), paused: Boolean = false) {
    val current = epg.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Logo(channel, size = 72)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text((if (paused) "❚❚  " else "") + "${channel.num}  ${channel.label}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            if (error != null) {
                Text(stringResource(R.string.stream_error, error), color = MaterialTheme.colorScheme.error)
            } else if (current != null) {
                Text("${hhmm.format(Date(current.start))}–${hhmm.format(Date(current.end))}  ${current.title}", color = Color.White)
                val progress = ((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Accent))
                }
                epg.getOrNull(1)?.let {
                    Text(stringResource(R.string.next_programme, hhmm.format(Date(it.start)), it.title), color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}
