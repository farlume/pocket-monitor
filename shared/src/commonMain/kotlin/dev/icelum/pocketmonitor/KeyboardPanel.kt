package dev.icelum.pocketmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.icelum.pocketmonitor.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun KeyboardPanel(sending: Boolean, actions: InputActions, preferences: AppPreferences,
    onPreferences: (AppPreferences) -> Unit) {
    var modifiers by remember { mutableIntStateOf(0) }
    var hold by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    fun release() { modifiers = 0; reset++; actions.cancel() }
    LaunchedEffect(sending) { if (sending) modifiers = 0 }
    LaunchedEffect(preferences.keyboardLayout) { modifiers = 0 }
    KeyboardOptions(preferences, onPreferences, !sending)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.input_hold_mode), Modifier.weight(1f))
        Switch(hold, { release(); hold = it }, enabled = !sending,
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
        FilterChip(keypad, { release(); keypad = !keypad }, label = { Text(stringResource(Res.string.input_keypad)) })
        TextButton({ release() }, Modifier.testTag("input_release_keys")) {
            Text(stringResource(Res.string.input_release_keys))
        }
    }
    if (setup) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.input_mac_setup_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Z" to 29, "/" to 56).forEach { (label, usage) ->
                        OutlinedButton({ release(); actions.key(HidStroke(usage)) },
                            enabled = !sending, modifier = Modifier.testTag("setup_key_$usage")) { Text(label) }
                    }
                }
            }
        }
    }
    Text(stringResource(Res.string.input_keyboard_pan_hint), style = MaterialTheme.typography.bodySmall)
    val tap: (Int) -> Unit = { usage ->
        if (usage in 0xE0..0xE7) modifiers = modifiers xor (1 shl (usage - 0xE0))
        else { actions.key(HidStroke(usage, modifiers)); modifiers = 0 }
    }
    TextButton({ release(); expanded = true }, Modifier.testTag("keyboard_expand")) {
        Text(stringResource(Res.string.keyboard_expand))
    }
    if (!expanded) key(preferences.keyboardLayout) {
        PhysicalKeyboardDeck(preferences, !sending, hold, modifiers, tap, actions, reset)
    }
    if (expanded) Dialog(onDismissRequest = { release(); expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ release(); expanded = false }, Modifier.testTag("keyboard_close")) {
                        Text(stringResource(Res.string.keyboard_close))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(Res.string.input_hold_mode))
                    Switch(hold, { release(); hold = it }, enabled = !sending,
                        modifier = Modifier.testTag("keyboard_expanded_hold"))
                    TextButton({ release() }, Modifier.testTag("keyboard_expanded_release")) {
                        Text(stringResource(Res.string.input_release_keys))
                    }
                }
                KeyboardOptions(preferences, onPreferences, !sending, compact = true, tagPrefix = "expanded_")
                key(preferences.keyboardLayout) {
                    PhysicalKeyboardDeck(preferences, !sending, hold, modifiers, tap, actions, reset, initialFit = true)
                }
            }
        }
    }
    if (keypad) {
        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnsiKeyboard.keypad.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key -> KeyboardKeycap(key, !sending, hold, false, preferences.keyboardHaptics,
                        keyboardPalette(preferences.keyboardTheme), tap, actions, Modifier.weight(key.width)) }
                    if (row.size == 3 && row.sumOf { it.width.toDouble() } == 3.0) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun modifierName(usage: Int): String = stringResource(when (usage) {
    0xE0 -> Res.string.input_left_ctrl
    0xE1 -> Res.string.input_left_shift
    0xE2 -> Res.string.input_left_alt
    0xE3 -> Res.string.input_left_meta
    0xE4 -> Res.string.input_right_ctrl
    0xE5 -> Res.string.input_right_shift
    0xE6 -> Res.string.input_right_alt
    else -> Res.string.input_right_meta
})
