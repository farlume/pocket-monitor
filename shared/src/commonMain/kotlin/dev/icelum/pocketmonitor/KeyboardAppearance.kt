package dev.icelum.pocketmonitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icelum.pocketmonitor.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal data class KeyboardPalette(val case: Color, val alphas: Color, val controls: Color,
    val accent: Color, val alphaInk: Color, val controlInk: Color, val accentInk: Color)

internal fun keyboardPalette(theme: KeyboardTheme) = when (theme) {
    KeyboardTheme.Ivory -> KeyboardPalette(Color(0xFF26292D), Color(0xFFF0EEE7), Color(0xFF474B50),
        Color(0xFFE76B43), Color(0xFF303338), Color(0xFFF4F2EB), Color(0xFF211B17))
    KeyboardTheme.Graphite -> KeyboardPalette(Color(0xFF25272B), Color(0xFF686C73), Color(0xFF3B3E44),
        Color(0xFFE2654F), Color(0xFFF5F5F3), Color(0xFFF5F5F3), Color(0xFF241B1A))
    KeyboardTheme.BlackGold -> KeyboardPalette(Color(0xFF252525), Color(0xFF414140), Color(0xFF30302F),
        Color(0xFFE5BD43), Color(0xFFF3CE64), Color(0xFFF3CE64), Color(0xFF272319))
    KeyboardTheme.Sea -> KeyboardPalette(Color(0xFFAEC6DB), Color(0xFFF0F5F7), Color(0xFF8DB3D1),
        Color(0xFF426C96), Color(0xFF315F88), Color(0xFF214C73), Color(0xFFF5F8FC))
}

@Composable
internal fun KeyboardOptions(preferences: AppPreferences, onPreferences: (AppPreferences) -> Unit, enabled: Boolean,
    compact: Boolean = false, tagPrefix: String = "") {
    var layoutMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton({ layoutMenu = true }, Modifier.fillMaxWidth().testTag("${tagPrefix}keyboard_layout"), enabled = enabled) {
                Text(stringResource(if (preferences.keyboardLayout == KeyboardLayout.Tkl87)
                    Res.string.keyboard_tkl87 else Res.string.keyboard_compact84), maxLines = 1)
            }
            DropdownMenu(layoutMenu, { layoutMenu = false }) {
                KeyboardLayout.entries.forEach { layout ->
                    DropdownMenuItem(text = { Text(stringResource(if (layout == KeyboardLayout.Tkl87)
                        Res.string.keyboard_tkl87 else Res.string.keyboard_compact84)) },
                        modifier = Modifier.testTag("${tagPrefix}keyboard_layout_${layout.name}"),
                        onClick = { onPreferences(preferences.copy(keyboardLayout = layout)); layoutMenu = false })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton({ themeMenu = true }, Modifier.fillMaxWidth().testTag("${tagPrefix}keyboard_theme"), enabled = enabled) {
                Text(themeName(preferences.keyboardTheme), maxLines = 1)
            }
            DropdownMenu(themeMenu, { themeMenu = false }) {
                KeyboardTheme.entries.forEach { theme ->
                    DropdownMenuItem(text = { Text(themeName(theme)) },
                        leadingIcon = { Box(Modifier.size(18.dp).background(keyboardPalette(theme).accent, RoundedCornerShape(4.dp))) },
                        modifier = Modifier.testTag("${tagPrefix}keyboard_theme_${theme.name}"),
                        onClick = { onPreferences(preferences.copy(keyboardTheme = theme)); themeMenu = false })
                }
            }
        }
    }
    if (!compact) {
        Text(stringResource(Res.string.keyboard_reference), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.keyboard_haptics), Modifier.weight(1f))
            Switch(preferences.keyboardHaptics, { onPreferences(preferences.copy(keyboardHaptics = it)) },
                modifier = Modifier.testTag("keyboard_haptics"))
        }
    }
}

@Composable
private fun themeName(theme: KeyboardTheme): String = stringResource(when (theme) {
    KeyboardTheme.Ivory -> Res.string.keyboard_ivory
    KeyboardTheme.Graphite -> Res.string.keyboard_graphite
    KeyboardTheme.BlackGold -> Res.string.keyboard_black_gold
    KeyboardTheme.Sea -> Res.string.keyboard_sea
})

@Composable
internal fun PhysicalKeyboardDeck(preferences: AppPreferences, enabled: Boolean, hold: Boolean,
    modifiers: Int, tap: (Int) -> Unit, actions: InputActions, reset: Int, initialFit: Boolean = false) {
    val board = PhysicalKeyboards.forLayout(preferences.keyboardLayout)
    val palette = keyboardPalette(preferences.keyboardTheme)
    var fit by remember { mutableStateOf(initialFit) }
    var lighting by remember { mutableStateOf(false) }
    var fn by remember { mutableStateOf(false) }
    val currentCancel by rememberUpdatedState(actions.cancel)
    DisposableEffect(Unit) { onDispose { currentCancel() } }
    LaunchedEffect(enabled, hold, reset) { fn = false }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(fit, { fit = !fit }, label = { Text(stringResource(Res.string.keyboard_fit)) }, modifier = Modifier.testTag("keyboard_fit"))
        FilterChip(lighting, { lighting = !lighting }, label = { Text(stringResource(Res.string.keyboard_lighting)) })
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pitch = if (fit) (maxWidth - 24.dp) / board.width else maxOf(48.dp, (maxWidth - 24.dp) / board.width)
        Box(Modifier.horizontalScroll(rememberScrollState()).testTag("physical_keyboard")) {
            Box(Modifier.width(pitch * board.width + 24.dp).height(pitch * board.height + 24.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(lerp(palette.case, Color.White, .12f), palette.case,
                    lerp(palette.case, Color.Black, .25f))), RoundedCornerShape(12.dp))
                .border(1.dp, lerp(palette.case, Color.White, .22f), RoundedCornerShape(12.dp))) {
                Box(Modifier.padding(8.dp).fillMaxSize().background(palette.case, RoundedCornerShape(7.dp))
                    .border(1.dp, Color.Black.copy(alpha = .35f), RoundedCornerShape(7.dp)))
                board.keys.forEach { positioned ->
                    key(positioned.key.usage) {
                        val usage = positioned.key.usage
                        val selected = (usage in 0xE0..0xE7 && modifiers and (1 shl (usage - 0xE0)) != 0) ||
                            (usage == PhysicalKeyboards.FN && fn) || (usage == PhysicalKeyboards.LIGHT && lighting)
                        val local = usage < 0
                        var heldUsage by remember { mutableStateOf<Int?>(null) }
                        val capActions = if (local) InputActions(
                            keyDown = { if (usage == PhysicalKeyboards.FN) fn = true else lighting = !lighting },
                            keyUp = { if (usage == PhysicalKeyboards.FN) fn = false },
                        ) else InputActions(
                            keyDown = { original ->
                                val reportUsage = if (fn) PhysicalKeyboards.fnUsage(original) else original
                                heldUsage = reportUsage
                                actions.keyDown(reportUsage)
                            },
                            keyUp = { heldUsage?.let(actions.keyUp); heldUsage = null },
                        )
                        KeyboardKeycap(positioned.key, enabled, hold, selected, preferences.keyboardHaptics, palette,
                            tap = { if (usage == PhysicalKeyboards.FN) fn = !fn
                                else if (usage == PhysicalKeyboards.LIGHT) lighting = !lighting
                                else { tap(if (fn) PhysicalKeyboards.fnUsage(it) else it); if (!hold) fn = false } }, actions = capActions,
                            modifier = Modifier.offset(12.dp + pitch * positioned.x, 12.dp + pitch * positioned.y)
                                .width(pitch * positioned.key.width).height(pitch), lighting = lighting,
                            compact = fit && pitch < 36.dp)
                    }
                }
            }
        }
    }
    if (fn) Text(stringResource(Res.string.keyboard_fn_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun KeyboardKeycap(key: KeyboardKey, enabled: Boolean, hold: Boolean, selected: Boolean,
    haptics: Boolean, palette: KeyboardPalette, tap: (Int) -> Unit, actions: InputActions,
    modifier: Modifier = Modifier, lighting: Boolean = false, compact: Boolean = false) {
    var pressed by remember { mutableStateOf(false) }
    var accessibilityPress by remember { mutableStateOf(false) }
    val currentTap by rememberUpdatedState(tap)
    val down by rememberUpdatedState(actions.keyDown)
    val up by rememberUpdatedState(actions.keyUp)
    val currentHaptics by rememberUpdatedState(haptics)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val travel by animateFloatAsState(if (pressed || accessibilityPress) 1f else 0f,
        tween(if (pressed || accessibilityPress) 35 else 110), label = "key travel")
    val control = key.usage !in 4..39 && key.usage !in 45..56 && key.usage != 44
    val accent = key.usage == 41 || (key.usage == 40 && palette.accent == Color(0xFFE5BD43))
    val plastic = if (accent) palette.accent else if (control) palette.controls else palette.alphas
    val ink = if (accent) palette.accentInk else if (control) palette.controlInk else palette.alphaInk
    val shape = RoundedCornerShape(if (compact) 3.dp else 5.dp)
    val description = when {
        key.usage in 0xE0..0xE7 -> modifierName(key.usage)
        key.usage == 44 -> stringResource(Res.string.keyboard_space)
        key.usage == PhysicalKeyboards.LIGHT -> stringResource(Res.string.keyboard_lighting)
        else -> key.legend
    }
    val face = lerp(plastic, palette.accent, if (selected) .32f else travel * .14f)
    Box(modifier.heightIn(min = if (compact) 0.dp else 48.dp).testTag("key_${key.usage}")
        .semantics {
            role = Role.Button
            contentDescription = description
            this.selected = selected
            if (!enabled) disabled()
            onClick {
                if (enabled) {
                    if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { accessibilityPress = true; delay(90); accessibilityPress = false }
                    currentTap(key.usage)
                }
                enabled
            }
        }
        .pointerInput(enabled, hold, key.usage) {
            if (enabled) detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (hold) down(key.usage)
                        tryAwaitRelease()
                    } finally {
                        if (hold) up(key.usage)
                        pressed = false
                    }
                },
                onTap = { if (!hold) currentTap(key.usage) },
            )
        }) {
        // Fixed hit area; only the sculpted cap moves into its socket.
        Box(Modifier.fillMaxSize().padding(1.dp).background(Color.Black.copy(alpha = .55f), shape))
        Box(Modifier.fillMaxSize().padding(2.dp).alpha(if (enabled) 1f else .45f)) {
            Box(Modifier.fillMaxSize().background(lerp(plastic, Color.Black, .38f), shape)
                .border(1.dp, if (lighting || selected) palette.accent.copy(alpha = .85f) else Color.Black.copy(alpha = .4f), shape))
            Box(Modifier.fillMaxSize().padding(bottom = if (compact) 2.dp else 4.dp)
                .offset(y = ((if (compact) 1.5f else 3f) * travel).dp)
                .shadow(((if (compact) 1f else 3f) * (1f - travel)).dp, shape)
                .background(Brush.verticalGradient(listOf(lerp(face, Color.White, .17f), face, lerp(face, Color.Black, .10f))), shape)
                .border(.7.dp, lerp(face, Color.White, .3f), shape)) {
                Box(Modifier.fillMaxSize().padding(horizontal = if (compact) 2.dp else 4.dp, vertical = if (compact) 1.dp else 3.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .06f), Color.Transparent,
                        Color.White.copy(alpha = .06f))), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    KeyLegend(key, ink, compact)
                }
            }
        }
    }
}

@Composable
private fun KeyLegend(key: KeyboardKey, ink: Color, compact: Boolean) {
    val numberShift = if (key.usage in 30..39) "!@#$%^&*()"[key.usage - 30].toString() else null
    val pair = if (numberShift != null) listOf(key.legend, numberShift) else key.legend.split(' ').takeIf { it.size == 2 && key.usage in 45..56 }
    val legend = when (key.usage) { 0xE1, 0xE5 -> "Shift"; 57 -> "Caps Lock"; else -> key.legend }
    if (pair != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pair[1], fontSize = if (compact) 7.sp else 10.sp, lineHeight = if (compact) 8.sp else 12.sp, color = ink.copy(alpha = .8f))
            Text(pair[0], fontSize = if (compact) 9.sp else 14.sp, lineHeight = if (compact) 10.sp else 16.sp, color = ink, fontWeight = FontWeight.Medium)
        }
    } else {
        Text(legend, fontSize = if (compact) { if (legend.length > 2) 6.sp else 9.sp }
            else if (key.usage in 4..29 || key.usage in 79..82) 14.sp else 10.sp,
            fontWeight = FontWeight.Medium, color = ink, maxLines = 1)
    }
}
