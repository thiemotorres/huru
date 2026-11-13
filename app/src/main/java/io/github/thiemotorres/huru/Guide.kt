package io.github.thiemotorres.huru

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val GUIDE_WINDOW = 2 * 60 * 60 * 1000L
private const val HALF_HOUR = 30 * 60 * 1000L
private val NAME_WIDTH = 240.dp

/** A programme to be reminded of (Menu in the live player); stored per source as [encode]d strings. */
data class Reminder(val channelId: Int, val start: Long, val title: String) {
    fun encode() = "$channelId|$start|$title"
}

fun decodeReminder(s: String): Reminder? = s.split('|', limit = 3).takeIf { it.size == 3 }?.let { (id, start, title) ->
    Reminder(id.toIntOrNull() ?: return null, start.toLongOrNull() ?: return null, title)
}

fun Programme.reminder(channel: Channel) = Reminder(channel.id, start, title)

/** Banner for a programme about to start (top of the live screens). */
@Composable
fun ReminderBanner(r: Reminder, channel: Channel, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(top = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(2.dp, Accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Logo(channel)
        Column {
            Text(stringResource(R.string.reminder_banner, hhmm.format(Date(r.start)), r.title, channel.label), color = Color.White)
            Text(stringResource(R.string.reminder_actions), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Current time, updated every minute. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000); value = System.currentTimeMillis() }
    }
    return now
}

// ponytail: rounded to UTC half hours; correct for all time zones with whole or half hour offsets.
fun guideStart(now: Long) = now - now % HALF_HOUR

@Composable
fun GuideHeader(start: Long, now: Long) {
    Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hhmm.format(Date(now)), Modifier.width(NAME_WIDTH), style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            for (i in 0 until (GUIDE_WINDOW / HALF_HOUR).toInt()) {
                Text(
                    hhmm.format(Date(start + i * HALF_HOUR)),
                    Modifier.offset(x = maxWidth * i / 4).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
fun GuideRow(
    channel: Channel,
    programmes: List<Programme>?, // null = still loading
    start: Long,
    now: Long,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = RoundedCornerShape(8.dp)),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(NAME_WIDTH), verticalAlignment = Alignment.CenterVertically) {
                Logo(channel)
                Text(
                    channel.label,
                    Modifier.weight(1f, fill = false).padding(start = 12.dp, end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                channel.quality?.let { Badge(it) }
                if (channel.archiveDays > 0) Text("⟲", Modifier.padding(start = 8.dp), color = Color.White.copy(alpha = 0.6f))
                if (favorite) Text("★", Modifier.padding(horizontal = 8.dp), color = Accent)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
                val end = start + GUIDE_WINDOW
                val visible = programmes.orEmpty().filter { it.end > start && it.start < end }
                if (programmes != null && visible.isEmpty()) {
                    Text(
                        stringResource(R.string.no_programme),
                        Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                        color = Color.White.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                for (p in visible) {
                    val from = maxOf(p.start, start)
                    val to = minOf(p.end, end)
                    val width = maxWidth * ((to - from).toFloat() / GUIDE_WINDOW)
                    val running = now in p.start until p.end
                    Box(
                        Modifier
                            .offset(x = maxWidth * ((from - start).toFloat() / GUIDE_WINDOW))
                            .width(width)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (running) Accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)),
                    ) {
                        if (running) { // elapsed part of the current programme
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(((now - from).toFloat() / (to - from)).coerceIn(0f, 1f))
                                    .background(Accent.copy(alpha = 0.25f)),
                            )
                        }
                        if (width > 56.dp) {
                            Text(
                                p.title,
                                Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Catch-up list of a channel (Right on a ⟲ channel). Traps focus until closed; [active] false while a recording plays
 * on top, then focus comes back to the last picked programme.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class) // focusProperties exit
fun ArchivePanel(x: Provider, channel: Channel, active: Boolean, onPlay: (Programme) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var items by remember { mutableStateOf<List<Programme>?>(null) }
    LaunchedEffect(channel) { items = runCatching { x.archive(channel) }.getOrDefault(emptyList()) }
    var picked by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(active, items) { if (active && items != null) { awaitFrame(); runCatching { focus.requestFocus() } } }
    val list = items
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)
    val runningLabel = stringResource(R.string.archive_running)
    fun day(t: Long): String {
        val days = (dayStart(System.currentTimeMillis()) - dayStart(t)) / 86_400_000L
        return when (days) { 0L -> today; 1L -> yesterday; else -> SimpleDateFormat("EE dd.MM.", Locale.getDefault()).format(Date(t)) }
    }
    LazyColumn(
        modifier
            .fillMaxHeight().width(560.dp)
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(32.dp)
            // Compose turns Back into a focus exit, which the trap below cancels – so close here.
            .onPreviewKeyEvent { e -> if (e.key == Key.Back && e.type == KeyEventType.KeyDown) { onClose(); true } else false }
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            // Nothing to focus while loading or empty: the panel itself takes focus so D-pad stays here.
            .then(if (list.isNullOrEmpty()) Modifier.focusRequester(focus).focusable() else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text(stringResource(R.string.archive_title, channel.label), style = MaterialTheme.typography.titleLarge, color = Color.White) }
        when {
            list == null -> item { Text(stringResource(R.string.loading), color = Color.White.copy(alpha = 0.6f)) }
            list.isEmpty() -> item { Text(stringResource(R.string.archive_empty), color = Color.White.copy(alpha = 0.6f)) }
            else -> itemsIndexed(list) { i, p ->
                ListItem(
                    selected = false,
                    onClick = { picked = i; onPlay(p) },
                    headlineContent = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val running = p.end > System.currentTimeMillis()
                        Text("${day(p.start)}  ${hhmm.format(Date(p.start))}–${hhmm.format(Date(p.end))}" + if (running) "  ·  $runningLabel" else "")
                    },
                    modifier = if (i == picked) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
