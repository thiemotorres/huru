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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.media3.common.VideoSize
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen live player. Up/Down (or CH+/CH−) zaps within the category, Right returns to the previous channel,
 * OK shows info, Back exits.
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // DefaultLoadControl
fun Player(
    x: Provider,
    channels: List<Channel>,
    start: Int,
    onChannel: (Channel) -> Unit,
    onWatched: (Channel) -> Unit,
    onStartOver: (Channel, Programme) -> Unit,
    reminders: Set<Reminder>,
    onRemind: (Reminder) -> Unit,
    onExit: (Channel) -> Unit, // the channel being watched
) {
    val context = LocalContext.current
    // Pin the list on start: a background refresh must not switch the channel mid-viewing.
    val channels = remember { channels }
    var index by remember { mutableIntStateOf(start) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val reconnect = remember { Reconnect(scope) }
    var typed by remember { mutableStateOf("") } // channel number being entered
    var epg by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(start) }
    var previous by remember { mutableStateOf<Int?>(null) } // channel before the current one (Right)
    val channel = channels[index]

    // Reads index state, not channel: the error listener below is created once and calls this later.
    fun play(p: ExoPlayer) {
        error = null
        p.setMediaItem(MediaItem.fromUri(x.streamUrl(channels[index])))
        p.prepare()
    }
    val player = remember {
        // Live TV: start after 1 s of buffer instead of 2.5 s (faster start and zapping).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
            .build()
        newPlayer(context).setLoadControl(loadControl).build().apply {
            playWhenReady = true
            addListener(object : MediaPlayer.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    when {
                        x.liveFallback() -> play(this@apply) // e.g. Xtream servers that only deliver HLS
                        reconnect.retry { play(this@apply) } -> Unit
                        else -> error = describe(e)
                    }
                }
                override fun onPlaybackStateChanged(state: Int) { if (state == MediaPlayer.STATE_READY) reconnect.reset() }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(channel) {
        if (index != shown) { previous = shown; shown = index }
        reconnect.reset()
        paused = false // zapping always plays the new channel
        player.playWhenReady = true
        play(player)
        onChannel(channel)
        overlayTick++
        epg = emptyList()
        epg = runCatching { x.shortEpg(channel) }.getOrDefault(emptyList())
    }
    // Separate from the guide request above, which can take long (big XMLTV files, slow servers).
    LaunchedEffect(channel) {
        delay(10_000) // only channels you stay on count as watched, not every zap
        onWatched(channel)
    }
    LaunchedEffect(overlayTick) { overlay = true; delay(4_000); overlay = false }
    // Number keys: switch once typing pauses (or after 4 digits).
    // ponytail: only finds channels of the list being zapped; switching category would break the way back to the list.
    LaunchedEffect(typed) {
        if (typed.isEmpty()) return@LaunchedEffect
        if (typed.length < 4) delay(1_500)
        channels.indexOfFirst { it.num == typed.toInt() }.takeIf { it >= 0 }?.let { index = it }
        typed = ""
    }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    // Also after the menu: its focus trap cancels a request made while it is still on screen.
    LaunchedEffect(menu) { if (!menu) runCatching { focus.requestFocus() } }
    BackHandler { onExit(channels[index]) }

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
                    Key.DirectionCenter, Key.Enter -> { if (error != null) { reconnect.reset(); play(player) }; overlayTick++; true }
                    // Remote Play/Pause (required for Fire TV media apps); resuming jumps back to the live edge.
                    // Continuing from the pause via catch-up was tried: panels serve recordings from a programme's start only
                    // and break off range requests into the running one.
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        paused = !paused
                        if (!paused) player.seekToDefaultPosition()
                        player.playWhenReady = !paused
                        overlayTick++
                        true
                    }
                    Key.DirectionLeft -> { startOver(channel, epg)?.let { onStartOver(channel, it) }; true }
                    Key.DirectionRight -> { previous?.let { index = it }; true } // also keeps focus from escaping
                    else -> digit(e)?.let { if (typed.length < 4) typed += it; true } ?: false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        VideoSurface(player) { onExit(channels[index]) }
        AnimatedVisibility(
            visible = overlay || paused || error != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val next = epg.getOrNull(1)
            InfoBar(
                channel, epg, error, paused = paused, startOver = startOver(channel, epg) != null,
                previous = previous?.let { channels.getOrNull(it) }, reminded = next != null && next.reminder(channel) in reminders,
            )
        }
        if (menu) {
            val next = epg.getOrNull(1)?.reminder(channel)
            TrackMenu(
                player, { menu = false }, Modifier.align(Alignment.CenterEnd),
                remind = next, reminded = next in reminders, onRemind = { next?.let(onRemind) },
            )
        }
        if (typed.isNotEmpty()) Text(
            typed,
            Modifier.align(Alignment.TopEnd).padding(48.dp).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 20.dp, vertical = 8.dp),
            color = Color.White, style = MaterialTheme.typography.displayMedium,
        )
    }
}

/** The digit of a number key (top row or numpad), else null. */
private fun digit(e: KeyEvent): Char? = when (val code = e.nativeKeyEvent.keyCode) {
    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_0)
    in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (code - android.view.KeyEvent.KEYCODE_NUMPAD_0)
    else -> null
}

/**
 * Streams drop now and then: retry quietly after 1, 2 and 4 s before the player shows the error.
 * ponytail: a stream that plays briefly and drops again retries forever; cap per minute if that annoys.
 */
class Reconnect(private val scope: CoroutineScope) {
    private var attempts = 0
    private var job: Job? = null

    /** Schedules [action]; false once the retries are used up. */
    fun retry(action: () -> Unit): Boolean {
        if (attempts >= 3) return false
        val wait = 1_000L shl attempts++ // counted now, not when the coroutine starts
        job = scope.launch { delay(wait); action() }
        return true
    }

    /** Playback works again, or a new stream starts: full retries, nothing pending. */
    fun reset() { attempts = 0; job?.cancel() }
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

/** Picture mode and sleep timer, shared by both players (menu sets them, the surface applies them). */
object Viewing {
    /** "fit", "fill" (zoom, crops) or "stretch"; null until read from the preferences. */
    var picture by mutableStateOf<String?>(null)
    /** Sleep timer: when playback stops (epoch ms) and the chosen minutes; not remembered across app starts. */
    var sleepAt by mutableStateOf<Long?>(null)
    var sleepMinutes by mutableIntStateOf(0)
}

/** Scale of a video with aspect [video] stretched over a screen with aspect [screen], per picture mode: (x, y). */
fun pictureScale(mode: String, video: Float, screen: Float): Pair<Float, Float> {
    if (!(video > 0f && screen > 0f) || mode == "stretch") return 1f to 1f // also catches NaN
    val k = video / screen // > 1: video is wider than the screen
    return if ((mode == "fit") == (k > 1)) 1f to 1f / k else k to 1f
}

/** Video with subtitles, picture mode and sleep timer; [onSleep] leaves the player when the timer runs out. */
@Composable
fun VideoSurface(player: ExoPlayer, onSleep: () -> Unit) {
    // Subtitles are drawn by hand: the TextureView has no subtitle layer.
    var cues by remember { mutableStateOf("") }
    var aspect by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val listener = object : MediaPlayer.Listener {
            override fun onCues(group: CueGroup) { cues = group.cues.mapNotNull { it.text }.joinToString("\n") }
            override fun onVideoSizeChanged(v: VideoSize) {
                aspect = if (v.height > 0) v.width * v.pixelWidthHeightRatio / v.height else 0f
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val prefs = trackPrefs(LocalContext.current)
    val picture = Viewing.picture ?: prefs.getString("picture", "fit")!!.also { Viewing.picture = it }
    val sleep by rememberUpdatedState(onSleep)
    LaunchedEffect(Viewing.sleepAt) {
        val at = Viewing.sleepAt ?: return@LaunchedEffect
        delay(at - System.currentTimeMillis())
        Viewing.sleepAt = null
        sleep()
    }
    // The texture is stretched over the view, so the view itself gets the size of the picture mode;
    // larger than the screen for fill (cropped). Transforms (graphicsLayer, setTransform) have no effect on Fire OS.
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val (x, y) = pictureScale(picture, aspect, maxWidth / maxHeight)
        AndroidView(
            // TextureView instead of PlayerView/SurfaceView: the SurfaceView ends up behind the Compose layer (audio, no picture).
            factory = { TextureView(it).apply { isFocusable = false; keepScreenOn = true; player.setVideoTextureView(this) } },
            modifier = Modifier.requiredSize(maxWidth * x, maxHeight * y),
        )
        // Fire OS fills the area below a shrunken TextureView with empty video memory (green): paint the bars black.
        if (x < 1f || y < 1f) Canvas(Modifier.fillMaxSize()) {
            val bar = Size(size.width * (1 - x) / 2, size.height * (1 - y) / 2)
            if (bar.height > 0) {
                drawRect(Color.Black, size = Size(size.width, bar.height))
                drawRect(Color.Black, Offset(0f, size.height - bar.height), Size(size.width, bar.height))
            }
            if (bar.width > 0) {
                drawRect(Color.Black, size = Size(bar.width, size.height))
                drawRect(Color.Black, Offset(size.width - bar.width, 0f), Size(bar.width, size.height))
            }
        }
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
fun TrackMenu(
    player: ExoPlayer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    remind: Reminder? = null, // live player: the next programme
    reminded: Boolean = false,
    onRemind: () -> Unit = {},
) {
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

    @Composable
    fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(top = 16.dp))

    LazyColumn(
        modifier.sidePanel(420.dp, onClose),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // On top and focused first: one press to set it.
        if (remind != null) {
            item { Text(stringResource(R.string.reminder), style = MaterialTheme.typography.titleLarge, color = Color.White) }
            item { Option("${hhmm.format(Date(remind.start))}  ${remind.title}", reminded, onRemind, Modifier.focusRequester(first)) }
            item { Heading(stringResource(R.string.audio)) }
        } else {
            item { Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleLarge, color = Color.White) }
        }
        itemsIndexed(audio) { n, (g, i) ->
            Option(audioLabels[n], g.isTrackSelected(i), { select(g, i, C.TRACK_TYPE_AUDIO) }, if (n == 0 && remind == null) Modifier.focusRequester(first) else Modifier)
        }
        item { Heading(stringResource(R.string.subtitles)) }
        item {
            Option(
                stringResource(R.string.subtitles_off), text.none { (g, i) -> g.isTrackSelected(i) }, { select(null, 0, C.TRACK_TYPE_TEXT) },
                if (audio.isEmpty() && remind == null) Modifier.focusRequester(first) else Modifier,
            )
        }
        itemsIndexed(text) { n, (g, i) -> Option(textLabels[n], g.isTrackSelected(i), { select(g, i, C.TRACK_TYPE_TEXT) }) }
        item { Heading(stringResource(R.string.picture)) }
        items(listOf("fit" to R.string.picture_fit, "fill" to R.string.picture_fill, "stretch" to R.string.picture_stretch)) { (mode, label) ->
            Option(stringResource(label), Viewing.picture == mode, {
                Viewing.picture = mode
                prefs.edit().putString("picture", mode).apply()
            })
        }
        item {
            val left = Viewing.sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000 + 1).toInt() }
            Heading(stringResource(R.string.sleep) + (left?.let { " · " + stringResource(R.string.sleep_left, it) } ?: ""))
        }
        items(listOf(0, 30, 60, 90)) { minutes ->
            Option(
                if (minutes == 0) stringResource(R.string.subtitles_off) else stringResource(R.string.minutes, minutes),
                (Viewing.sleepAt == null && minutes == 0) || (Viewing.sleepAt != null && Viewing.sleepMinutes == minutes),
                {
                    Viewing.sleepMinutes = minutes
                    Viewing.sleepAt = if (minutes == 0) null else System.currentTimeMillis() + minutes * 60_000L
                },
            )
        }
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

/** The running programme, if the channel's catch-up can play it from its start (Left in the live player). */
fun startOver(channel: Channel, epg: List<Programme>, now: Long = System.currentTimeMillis()): Programme? =
    epg.firstOrNull()?.takeIf { channel.archiveDays > 0 && it.serverStart != null && now in it.start until it.end }

@Composable
internal fun InfoBar(
    channel: Channel,
    epg: List<Programme>,
    error: String?,
    now: Long = System.currentTimeMillis(),
    paused: Boolean = false,
    startOver: Boolean = false,
    previous: Channel? = null,
    reminded: Boolean = false,
) {
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
                    Text(
                        stringResource(R.string.next_programme, hhmm.format(Date(it.start)), it.title) + if (reminded) "  🔔" else "",
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            val hints = listOfNotNull(
                stringResource(R.string.start_over_hint).takeIf { startOver && error == null },
                previous?.let { stringResource(R.string.previous_hint, it.label) },
            )
            if (hints.isNotEmpty()) Text(hints.joinToString("   "), color = Accent)
        }
    }
}
