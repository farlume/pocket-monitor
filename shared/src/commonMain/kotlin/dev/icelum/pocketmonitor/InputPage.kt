package dev.icelum.pocketmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.icelum.pocketmonitor.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

class InputActions(
    val enable: () -> Unit = {}, val disable: () -> Unit = {},
    val settings: () -> Unit = {}, val discoverable: () -> Unit = {},
    val refresh: () -> Unit = {}, val connect: (String) -> Unit = {},
    val key: (HidStroke) -> Unit = {}, val text: (String) -> Boolean = { false },
    val move: (Int, Int, Int) -> Unit = { _, _, _ -> }, val click: (Boolean) -> Unit = {},
    val cancel: () -> Unit = {},
    val keyDown: (Int) -> Unit = {}, val keyUp: (Int) -> Unit = {},
)

@Composable
fun InputPage(state: InputState, actions: InputActions, modifier: Modifier = Modifier,
    preferences: AppPreferences = AppPreferences(), onPreferences: (AppPreferences) -> Unit = {}) {
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.phase == InputPhase.Connected) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.host?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(actions.disable, Modifier.testTag("input_disable")) { Text(stringResource(Res.string.input_disable)) }
            }
        } else {
            Text(stringResource(Res.string.input_intro), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(when (state.phase) {
                InputPhase.Off -> Res.string.input_off
                InputPhase.Unsupported -> Res.string.input_unsupported
                InputPhase.PermissionRequired -> Res.string.input_permission
                InputPhase.BluetoothOff -> Res.string.input_bluetooth_off
                InputPhase.Starting -> Res.string.input_starting
                InputPhase.Ready -> Res.string.input_ready
                InputPhase.Connecting -> Res.string.input_connecting
                InputPhase.Connected -> Res.string.input_connected
                InputPhase.Error -> Res.string.input_error
            }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("input_status"))
            state.host?.let { Text(it.name + " · " + it.id, style = MaterialTheme.typography.bodySmall) }
            if (state.phase in listOf(InputPhase.Off, InputPhase.PermissionRequired, InputPhase.BluetoothOff, InputPhase.Error)) {
                Button(actions.enable, Modifier.testTag("input_enable")) { Text(stringResource(Res.string.input_enable)) }
            }
            if (state.phase != InputPhase.Unsupported) {
                TextButton(actions.settings) { Text(stringResource(Res.string.input_system_settings)) }
            }
            if (state.phase == InputPhase.Starting || state.phase == InputPhase.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase in listOf(InputPhase.Starting, InputPhase.Ready, InputPhase.Connecting, InputPhase.Connected)) {
                OutlinedButton(actions.disable, Modifier.testTag("input_disable")) { Text(stringResource(Res.string.input_disable)) }
            }
            if (state.phase == InputPhase.Ready) {
                Text(stringResource(Res.string.input_pair_help), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(actions.discoverable) { Text(stringResource(Res.string.input_pair)) }
                    TextButton(actions.refresh) { Text(stringResource(Res.string.input_refresh)) }
                }
                if (state.hosts.isEmpty()) Text(stringResource(Res.string.input_no_hosts))
                state.hosts.forEach { host ->
                    OutlinedButton({ actions.connect(host.id) }, Modifier.fillMaxWidth().testTag("input_host_${host.id}")) {
                        Column { Text(host.name); Text(host.id, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        if (state.phase == InputPhase.Connected) InputControls(state, actions, preferences, onPreferences)
        Text(stringResource(Res.string.input_limitations), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InputControls(state: InputState, actions: InputActions, preferences: AppPreferences,
    onPreferences: (AppPreferences) -> Unit) {
    var text by remember { mutableStateOf("") }
    var pointerMode by remember { mutableStateOf(false) }
    val invalid = text.isNotEmpty() && HidReports.text(text) == null
    val currentCancel by rememberUpdatedState(actions.cancel)
    DisposableEffect(state.host?.id) { onDispose { currentCancel() } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!pointerMode, { pointerMode = false }, enabled = !state.sending,
            label = { Text(stringResource(Res.string.input_keyboard)) })
        FilterChip(pointerMode, { pointerMode = true; actions.cancel() }, enabled = !state.sending,
            label = { Text(stringResource(Res.string.input_touchpad)) }, modifier = Modifier.testTag("input_mouse_mode"))
    }
    if (pointerMode) {
        Touchpad(state, actions)
        return
    }
    KeyboardPanel(state.sending, actions, preferences, onPreferences)
    OutlinedTextField(value = text, onValueChange = { if (it.length <= HidReports.MAX_TEXT) text = it },
        modifier = Modifier.fillMaxWidth().testTag("input_text"), enabled = !state.sending,
        label = { Text(stringResource(Res.string.input_text)) }, isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        supportingText = { Text(stringResource(if (invalid) Res.string.input_invalid_text else Res.string.input_text_hint)) })
    Button(onClick = { if (actions.text(text)) { text = "" } },
        enabled = text.isNotEmpty() && !invalid && !state.sending, modifier = Modifier.testTag("input_send")) {
        Text(stringResource(Res.string.input_send))
    }
    if (state.sending) TextButton(actions.cancel, Modifier.testTag("input_cancel")) { Text(stringResource(Res.string.input_cancel)) }
}

@Composable
private fun Touchpad(state: InputState, actions: InputActions) {
    Text(stringResource(Res.string.input_touchpad), style = MaterialTheme.typography.titleSmall)
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
        .testTag("input_touchpad")
        .pointerInput(state.sending) {
            var remainderX = 0f
            var remainderY = 0f
            if (!state.sending) detectDragGestures(onDragStart = { remainderX = 0f; remainderY = 0f }) { change, delta ->
                change.consume()
                remainderX += delta.x / density; remainderY += delta.y / density
                val x = remainderX.roundToInt(); val y = remainderY.roundToInt()
                remainderX -= x; remainderY -= y
                if (x != 0 || y != 0) actions.move(x, y, 0)
            }
        }.pointerInput(state.sending) { if (!state.sending) detectTapGestures { actions.click(false) } },
        contentAlignment = Alignment.Center) { Text(stringResource(Res.string.input_touchpad_hint)) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ actions.click(false) }, enabled = !state.sending) { Text(stringResource(Res.string.input_left_click)) }
        OutlinedButton({ actions.click(true) }, enabled = !state.sending) { Text(stringResource(Res.string.input_right_click)) }
        OutlinedButton({ actions.move(0, 0, 1) }, enabled = !state.sending) { Text(stringResource(Res.string.input_scroll_up)) }
        OutlinedButton({ actions.move(0, 0, -1) }, enabled = !state.sending) { Text(stringResource(Res.string.input_scroll_down)) }
    }
}
