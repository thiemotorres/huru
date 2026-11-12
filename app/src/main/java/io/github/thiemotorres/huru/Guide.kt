package io.github.thiemotorres.huru

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import java.util.Date

const val GUIDE_WINDOW = 2 * 60 * 60 * 1000L
private const val HALF_HOUR = 30 * 60 * 1000L
private val NAME_WIDTH = 240.dp

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
