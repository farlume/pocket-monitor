package dev.icelum.pocketmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icelum.pocketmonitor.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun KeyboardPanel(sending: Boolean, actions: InputActions) {
    var modifiers by remember { mutableIntStateOf(0) }
    var hold by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    LaunchedEffect(sending) { if (sending) modifiers = 0 }
    Text(stringResource(Res.string.input_ansi_keyboard), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.input_hold_mode), Modifier.weight(1f))
        Switch(hold, { actions.cancel(); modifiers = 0; hold = it }, enabled = !sending,
            modifier = Modifier.testTag("input_hold_mode"))
    }
    Text(stringResource(if (hold) Res.string.input_hold_hint else Res.string.input_modifier_hint),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!hold) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0xE0, 0xE1, 0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7).forEach { usage ->
                val mask = 1 shl (usage - 0xE0)
                FilterChip(modifiers and mask != 0, { modifiers = modifiers xor mask }, enabled = !sending,
                    label = { Text(modifierName(usage)) }, modifier = Modifier.testTag("modifier_$mask"))
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ setup = !setup }, Modifier.testTag("input_mac_setup")) { Text(stringResource(Res.string.input_mac_setup)) }
        FilterChip(keypad, { actions.cancel(); keypad = !keypad }, label = { Text(stringResource(Res.string.input_keypad)) })
        TextButton({ modifiers = 0; actions.cancel() }, Modifier.testTag("input_release_keys")) {
            Text(stringResource(Res.string.input_release_keys))
        }
    }
    if (setup) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.input_mac_setup_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Z" to 29, "/" to 56).forEach { (label, usage) ->
                        OutlinedButton({ modifiers = 0; actions.cancel(); actions.key(HidStroke(usage)) },
                            enabled = !sending, modifier = Modifier.testTag("setup_key_$usage")) { Text(label) }
                    }
                }
            }
        }
    }
    Text(stringResource(Res.string.input_keyboard_pan_hint), style = MaterialTheme.typography.bodySmall)
    val tap: (Int) -> Unit = { usage -> actions.key(HidStroke(usage, modifiers)); modifiers = 0 }
    // One shared horizontal viewport keeps ANSI positions aligned across all rows.
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))) {
        val keyboardWidth = maxOf(maxWidth, 784.dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(keyboardWidth).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                (listOf(AnsiKeyboard.functionRow) + AnsiKeyboard.rows + listOf(AnsiKeyboard.navigation)).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { key -> KeyboardKeycap(key, !sending, hold, tap, actions,
                            Modifier.weight(key.width)) }
                    }
                }
            }
        }
    }
    if (keypad) {
        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnsiKeyboard.keypad.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key -> KeyboardKeycap(key, !sending, hold, tap, actions, Modifier.weight(key.width)) }
                    if (row.size == 3 && row.sumOf { it.width.toDouble() } == 3.0) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun modifierName(usage: Int): String = stringResource(when (usage) {
    0xE0 -> Res.string.input_left_ctrl
    0xE1 -> Res.string.input_left_shift
    0xE2 -> Res.string.input_left_alt
    0xE3 -> Res.string.input_left_meta
    0xE4 -> Res.string.input_right_ctrl
    0xE5 -> Res.string.input_right_shift
    0xE6 -> Res.string.input_right_alt
    else -> Res.string.input_right_meta
})

@Composable
private fun KeyboardKeycap(key: KeyboardKey, enabled: Boolean, hold: Boolean, tap: (Int) -> Unit,
    actions: InputActions, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val currentTap by rememberUpdatedState(tap)
    val down by rememberUpdatedState(actions.keyDown)
    val up by rememberUpdatedState(actions.keyUp)
    val description = if (key.usage in 0xE0..0xE7) modifierName(key.usage) else key.legend
    Box(modifier.height(48.dp).testTag("key_${key.usage}")
        .background(if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            RoundedCornerShape(7.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
        .semantics {
            role = Role.Button
            contentDescription = description
            if (!enabled) disabled()
            onClick { if (enabled) currentTap(key.usage); enabled }
        }
        .pointerInput(enabled, hold, key.usage) {
            if (enabled) detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        if (hold) down(key.usage)
                        tryAwaitRelease()
                    } finally {
                        if (hold) up(key.usage)
                        pressed = false
                    }
                },
                onTap = { if (!hold) currentTap(key.usage) },
            )
        }, contentAlignment = Alignment.Center) {
        Text(key.legend, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
