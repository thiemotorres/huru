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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/** Settings: manage sources (switch, edit, delete, add) next to the about panel. */
@Composable
fun SettingsScreen(
    header: @Composable () -> Unit,
    sources: List<Account>,
    active: Account,
    onActivate: (Account) -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    var form by remember { mutableStateOf<Account?>(null) } // source being edited
    var adding by remember { mutableStateOf(false) }
    if (adding || form != null) {
        SourceForm(
            initial = form,
            onSave = { onSave(it); form = null; adding = false },
            onCancel = { form = null; adding = false },
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        header()
        Row(Modifier.fillMaxSize().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sources_title), style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(sources, key = { it.id }) { source -> SourceRow(source, source == active, onActivate, { form = it }, onDelete) }
                    item {
                        Button(onClick = { adding = true }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.source_add)) }
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
            modifier = Modifier.weight(1f),
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
