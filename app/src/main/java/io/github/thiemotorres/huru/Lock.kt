package io.github.thiemotorres.huru

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.android.awaitFrame

/** Unlocked after the right PIN until Huru goes to the background (reset in MainActivity.onStop). */
object Session {
    var open by mutableStateOf(false)
}

private val ADULT = Regex("""\bxxx\b|\badults?\b|18\s*\+|\bporn|\berotic|\berotik""", RegexOption.IGNORE_CASE)

/** Adult-looking category names get locked automatically once a PIN exists. */
fun isAdult(name: String) = ADULT.containsMatchIn(name)

/**
 * Parental lock. The PIN is device-wide; locked categories are per source, keyed "kind:id"
 * (kind = live/movie/series, the id spaces may overlap). [released] holds adult categories the user unlocked.
 */
class Lock(context: Context, source: Account) {
    private val pinPrefs = context.getSharedPreferences("lock", Context.MODE_PRIVATE)
    private val prefs = context.sourcePrefs("categories", source)
    var pin by mutableStateOf(pinPrefs.getString("pin", null))
        private set
    private var locked by mutableStateOf(prefs.getStringSet("locked", emptySet())!!.toSet())
    private var released by mutableStateOf(prefs.getStringSet("released", emptySet())!!.toSet())

    /** Configured as locked (shown with 🔒), regardless of the session. */
    fun isLocked(kind: String, cat: Category): Boolean {
        val key = "$kind:${cat.id}"
        return pin != null && (key in locked || isAdult(cat.name) && key !in released)
    }

    /** Ids whose content is hidden right now. */
    fun closedIds(kind: String, cats: List<Category>): Set<String> =
        if (Session.open) emptySet() else cats.filter { isLocked(kind, it) }.map { it.id }.toSet()

    fun setLocked(kind: String, cat: Category, on: Boolean) {
        val key = "$kind:${cat.id}"
        locked = if (on) locked + key else locked - key
        released = if (on || !isAdult(cat.name)) released - key else released + key
        prefs.edit().putStringSet("locked", locked).putStringSet("released", released).apply()
    }

    fun savePin(value: String?) {
        pin = value
        pinPrefs.edit().putString("pin", value).apply()
    }
}

private enum class Step { VERIFY, NEW, REPEAT }

/**
 * Asks for the PIN and calls [onOk] after the right one (that also opens the [Session]).
 * Without a PIN, or with [change], it sets a new one (entered twice) – setting one does not open the session.
 */
@Composable
fun PinGate(lock: Lock, change: Boolean = false, onOk: () -> Unit, onCancel: () -> Unit) {
    var step by remember { mutableStateOf(if (lock.pin != null) Step.VERIFY else Step.NEW) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val wrong = stringResource(R.string.pin_wrong)
    val mismatch = stringResource(R.string.pin_mismatch)
    val title = stringResource(
        when (step) { Step.VERIFY -> R.string.pin_enter; Step.NEW -> R.string.pin_new; Step.REPEAT -> R.string.pin_repeat },
    )
    key(attempt, step) {
        PinDialog(title, error, onCancel) { entered ->
            when (step) {
                Step.VERIFY ->
                    if (entered != lock.pin) { error = wrong; attempt++ }
                    else { Session.open = true; error = null; if (change) step = Step.NEW else onOk() }
                Step.NEW -> { first = entered; error = null; step = Step.REPEAT }
                Step.REPEAT ->
                    if (entered == first) { lock.savePin(entered); onOk() }
                    else { error = mismatch; step = Step.NEW }
            }
        }
    }
}

/** Four digits for the D-pad: Up/Down change, Left/Right or OK move, OK on the last one confirms. Number keys work too. */
@Composable
private fun PinDialog(title: String, error: String?, onCancel: () -> Unit, onEnter: (String) -> Unit) {
    val digits = remember { mutableStateListOf(0, 0, 0, 0) }
    var pos by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { focus.requestFocus() } }
    fun next() { if (pos < 3) pos++ else onEnter(digits.joinToString("")) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent true // swallow everything: nothing leaks to the screen below
                val number = e.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_0
                when {
                    e.key == Key.Back -> onCancel()
                    e.key == Key.DirectionUp -> digits[pos] = (digits[pos] + 1) % 10
                    e.key == Key.DirectionDown -> digits[pos] = (digits[pos] + 9) % 10
                    e.key == Key.DirectionLeft -> pos = (pos - 1).coerceAtLeast(0)
                    e.key == Key.DirectionRight -> pos = (pos + 1).coerceAtMost(3)
                    e.key == Key.DirectionCenter || e.key == Key.Enter -> next()
                    number in 0..9 -> { digits[pos] = number; next() }
                }
                true
            }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                digits.forEachIndexed { i, d ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.size(72.dp)
                            .background(Color.White.copy(alpha = 0.08f), shape)
                            .then(if (i == pos) Modifier.border(2.dp, Accent, shape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Only the digit being edited is visible.
                        Text(if (i == pos) "$d" else if (i < pos) "•" else "–", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }
            }
            Text(stringResource(R.string.pin_help), color = Color.White.copy(alpha = 0.6f))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Long-press menu of a category: hide (live only) and lock. Traps focus until closed. */
@Composable
@OptIn(ExperimentalComposeUiApi::class) // focusProperties exit
fun CategoryMenu(title: String, locked: Boolean, hidden: Boolean?, onToggleHidden: () -> Unit, onToggleLock: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(420.dp)
                .background(Night, RoundedCornerShape(12.dp))
                .padding(24.dp)
                // Compose turns Back into a focus exit, which the trap below cancels – so close here.
                .onPreviewKeyEvent { e -> if (e.key == Key.Back && e.type == KeyEventType.KeyDown) { onClose(); true } else false }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            ListItem(
                selected = false, onClick = onToggleLock, modifier = Modifier.focusRequester(first),
                headlineContent = { Text(stringResource(if (locked) R.string.lock_remove else R.string.lock_add)) },
            )
            if (hidden != null) ListItem(
                selected = false, onClick = onToggleHidden,
                headlineContent = { Text(stringResource(if (hidden) R.string.category_show else R.string.category_hide)) },
            )
        }
    }
}

/** Shown instead of the content of a locked category. */
@Composable
fun LockedHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.locked_hint), color = Color.White.copy(alpha = 0.7f))
    }
}
