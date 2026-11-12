package dev.iptv

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as MediaPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Vollbild-Player. Hoch/Runter (oder CH+/CH−) zappt innerhalb der Kategorie, OK zeigt Info, Zurück verlässt. */
@Composable
fun Player(x: Xtream, channels: List<Channel>, start: Int, onExit: (Int) -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(start) }
    var error by remember { mutableStateOf<String?>(null) }
    var epg by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    val channel = channels[index]

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : MediaPlayer.Listener {
                override fun onPlayerError(e: PlaybackException) { error = e.errorCodeName }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun play() {
        error = null
        player.setMediaItem(MediaItem.fromUri(x.streamUrl(channel)))
        player.prepare()
    }

    LaunchedEffect(channel) {
        play()
        overlayTick++
        epg = emptyList()
        epg = runCatching { x.shortEpg(channel) }.getOrDefault(emptyList())
    }
    LaunchedEffect(overlayTick) { overlay = true; delay(4_000); overlay = false }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler { onExit(index) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { index = (index - 1 + channels.size) % channels.size; true }
                    Key.DirectionDown, Key.ChannelDown -> { index = (index + 1) % channels.size; true }
                    Key.DirectionCenter, Key.Enter -> { if (error != null) play(); overlayTick++; true }
                    Key.DirectionLeft, Key.DirectionRight -> true // nicht in die Liste dahinter ausbrechen
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        AndroidView(
            // TextureView statt PlayerView/SurfaceView: die SurfaceView landet unter der Compose-Fläche (Ton, kein Bild).
            factory = { TextureView(it).apply { isFocusable = false; keepScreenOn = true; player.setVideoTextureView(this) } },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = overlay || error != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoBar(channel, epg, error)
        }
    }
}

private val hhmm = SimpleDateFormat("HH:mm", Locale.GERMANY)

@Composable
private fun InfoBar(channel: Channel, epg: List<Programme>, error: String?) {
    val now = epg.firstOrNull()
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
            Text("${channel.num}  ${channel.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            if (error != null) {
                Text("Stream-Fehler: $error · OK zum Neuladen", color = MaterialTheme.colorScheme.error)
            } else if (now != null) {
                Text("${hhmm.format(Date(now.start))}–${hhmm.format(Date(now.end))}  ${now.title}", color = Color.White)
                val progress = ((System.currentTimeMillis() - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Accent))
                }
                epg.getOrNull(1)?.let {
                    Text("Danach ${hhmm.format(Date(it.start))}  ${it.title}", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}
