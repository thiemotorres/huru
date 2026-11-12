package io.github.thiemotorres.huru

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/** Focus target for the "add source" button. */
const val ADD_SOURCE = "__add_source"
private const val PIN_BUTTON = "__pin"

/** Settings: manage sources (switch, edit, delete, add) next to the about panel. */
@Composable
fun SettingsScreen(
    header: @Composable () -> Unit,
    sources: List<Account>,
    active: Account,
    focusId: String?,
    onFocusId: (String?) -> Unit,
    onActivate: (Account) -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    val context = LocalContext.current
    var form by remember { mutableStateOf<Account?>(null) } // source being edited
    var adding by remember { mutableStateOf(false) }
    val lock = remember(active) { Lock(context, active) }
    var pinAction by remember { mutableStateOf<String?>(null) } // "change" or "remove"
    pinAction?.let { action ->
        PinGate(
            lock, change = action == "change",
            onOk = { if (action == "remove") lock.savePin(null); pinAction = null; onFocusId(PIN_BUTTON) },
            onCancel = { pinAction = null; onFocusId(PIN_BUTTON) },
        )
        return
    }
    if (adding || form != null) {
        SourceForm(
            initial = form,
            onSave = { onSave(it); form = null; adding = false },
            onCancel = { onFocusId(form?.id ?: ADD_SOURCE); form = null; adding = false },
        )
        return
    }
    // Focus the row we came from (saved, edited, neighbour of a deleted one) instead of the mode switch.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }
    LaunchedEffect(focusId) {
        val id = focusId ?: return@LaunchedEffect
        awaitFrame()
        runCatching { requester(id).requestFocus() }
        onFocusId(null)
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        header()
        Row(Modifier.fillMaxSize().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sources_title), style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(sources, key = { it.id }) { source ->
                        SourceRow(source, source == active, Modifier.focusRequester(requester(source.id)), onActivate, { form = it }, onDelete)
                    }
                    item {
                        Button(onClick = { adding = true }, Modifier.padding(top = 8.dp).focusRequester(requester(ADD_SOURCE))) {
                            Text(stringResource(R.string.source_add))
                        }
                    }
                    item {
                        Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.parental_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.parental_hint), color = Color.White.copy(alpha = 0.7f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pinAction = "change" }, Modifier.focusRequester(requester(PIN_BUTTON))) {
                                    Text(stringResource(if (lock.pin == null) R.string.pin_set else R.string.pin_change))
                                }
                                if (lock.pin != null) OutlinedButton(onClick = { pinAction = "remove" }) { Text(stringResource(R.string.pin_remove)) }
                            }
                        }
                    }
                    item {
                        val prefs = displayPrefs(context)
                        var flags by remember { mutableStateOf(prefs.getBoolean("flags", false)) }
                        Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleLarge)
                            ListItem(
                                selected = false,
                                onClick = { flags = !flags; prefs.edit().putBoolean("flags", flags).apply() },
                                headlineContent = { Text(stringResource(R.string.display_flags)) },
                                trailingContent = { Switch(checked = flags, onCheckedChange = null) },
                            )
                        }
                    }
                }
            }
            AboutPanel(Modifier.width(380.dp))
        }
    }
}

@Composable
private fun SourceRow(
    source: Account,
    active: Boolean,
    focus: Modifier,
    onActivate: (Account) -> Unit,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListItem(
            selected = active,
            onClick = { onActivate(source) },
            headlineContent = { Text(source.name.ifBlank { hostOf(source.server) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("${hostOf(source.server)} · ${source.user}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = if (active) { { Badge(stringResource(R.string.source_active)) } } else null,
            modifier = Modifier.weight(1f).then(focus),
        )
        OutlinedButton(onClick = { onEdit(source) }) { Text(stringResource(R.string.source_edit)) }
        OutlinedButton(onClick = { if (confirm) onDelete(source) else confirm = true }) {
            Text(stringResource(if (confirm) R.string.source_delete_confirm else R.string.source_delete))
        }
    }
}

/**
 * Add or edit a source. Validates the login before saving. Without [onCancel] it is the first-run setup,
 * which also offers the public demo server.
 */
@Composable
fun SourceForm(initial: Account?, onSave: (Account) -> Unit, onCancel: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var server by remember { mutableStateOf(initial?.server.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.pass.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val loginFailed = stringResource(R.string.login_failed, "%s")
    val connectionFailed = stringResource(R.string.connection_failed)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    fun submit(candidate: Account) {
        busy = true; error = null
        scope.launch {
            runCatching { Xtream(candidate).login() }
                .onSuccess {
                    // Otherwise the IME session stays open and Fire OS re-shows the keyboard over the player.
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                    onSave(candidate)
                }
                .onFailure { error = it.message?.let { m -> loginFailed.format(m) } ?: connectionFailed; busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(when { onCancel == null -> R.string.setup_welcome; initial == null -> R.string.source_add_title; else -> R.string.source_edit_title }),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (onCancel == null) Text(stringResource(R.string.setup_hint), color = Color.White.copy(alpha = 0.7f))
        Box(Modifier.focusRequester(first)) { Field(stringResource(R.string.source_name), name) { name = it } }
        Field(stringResource(R.string.login_server), server, KeyboardType.Uri) { server = it }
        Field(stringResource(R.string.login_user), user) { user = it }
        Field(stringResource(R.string.login_password), pass, KeyboardType.Password, password = true) { pass = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(enabled = !busy && server.isNotBlank(), onClick = {
                submit(Account(server.trim(), user.trim(), pass, id = initial?.id ?: newSourceId(), name = name.trim().ifBlank { hostOf(server) }))
            }) { Text(stringResource(if (busy) R.string.login_connecting else if (initial == null) R.string.login_submit else R.string.source_save)) }
            if (initial == null) {
                OutlinedButton(enabled = !busy, onClick = { submit(DEMO_SOURCE.copy(id = newSourceId())) }) { Text(stringResource(R.string.try_demo)) }
            }
            if (onCancel != null) OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
