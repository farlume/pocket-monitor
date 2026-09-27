package dev.icelum.pocketmonitor

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icelum.pocketmonitor.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    state: CaptureState,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    onConnect: (String?) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onMode: (VideoMode) -> Unit,
    onAppSettings: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    preferences: AppPreferences = AppPreferences(),
    onPreferences: (AppPreferences) -> Unit = {},
    selectedTab: AppTab? = null,
    onTab: (AppTab) -> Unit = {},
    appVersion: String = "",
    onOpenLicenses: () -> Unit = {},
    inputState: InputState = InputState(),
    inputActions: InputActions = InputActions(),
    onDiagnostics: suspend () -> String = { "" },
) {
    var internalTab by rememberSaveable { mutableStateOf(AppTab.Preview) }
    val tab = selectedTab ?: internalTab
    var inputPanel by remember { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var diagnosticReport by remember { mutableStateOf<String?>(null) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var diagnosticJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun closeDiagnostics() { diagnosticJob?.cancel(); diagnosticsOpen = false }
    val clipboard = LocalClipboardManager.current
    fun selectTab(next: AppTab) { internalTab = next; onTab(next) }
    MonitorTheme(preferences) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compact = maxHeight < 500.dp
                Column(Modifier.fillMaxSize()) {
                    if (!fullscreen && (!compact || tab != AppTab.Preview)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Monitor, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(when (tab) {
                                    AppTab.Preview -> stringResource(Res.string.app_name)
                                    AppTab.Devices -> stringResource(Res.string.tab_devices)
                                    AppTab.Input -> stringResource(Res.string.tab_input)
                                    AppTab.Settings -> stringResource(Res.string.tab_settings)
                                }, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text("POCKET MONITOR", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            IconButton(onClick = { help = true }, modifier = Modifier.testTag("help")) {
                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(Res.string.connection_guide))
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // Keep the native surface mounted across tabs. Opaque page surfaces cover it,
                        // and hidden preview controls are removed from accessibility traversal.
                        Box(Modifier.fillMaxSize().alpha(if (tab == AppTab.Preview) 1f else 0f)
                            .then(if (tab != AppTab.Preview) Modifier.clearAndSetSemantics {} else Modifier)) {
                            PreviewPane(state, fullscreen, compact, preferences, onFullscreen,
                                { id -> if (id == null && state.devices.size > 1) selectTab(AppTab.Devices) else onConnect(id) },
                                onRefresh, onDisconnect, onAppSettings, { selectTab(AppTab.Devices) }, { inputPanel = true }, preview)
                        }
                        when (tab) {
                            AppTab.Preview -> Unit
                            AppTab.Devices -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                DevicePage(state, { onConnect(it); selectTab(AppTab.Preview) }, onRefresh,
                                    onDisconnect, { onMode(it); selectTab(AppTab.Preview) },
                                    onDiagnostics = {
                                        if (!diagnosticsOpen) {
                                            diagnosticsOpen = true
                                            diagnosticReport = null
                                            diagnosticJob = scope.launch { diagnosticReport = onDiagnostics() }
                                        }
                                    })
                            }
                            AppTab.Input -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                InputPage(inputState, inputActions)
                            }
                            AppTab.Settings -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                SettingsPage(preferences, onPreferences, appVersion, onOpenLicenses)
                            }
                        }
                    }
                    if (!fullscreen) NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
                        AppTab.entries.forEach { item ->
                            val title = when (item) {
                                AppTab.Preview -> stringResource(Res.string.tab_preview)
                                AppTab.Devices -> stringResource(Res.string.tab_devices)
                                AppTab.Input -> stringResource(Res.string.tab_input)
                                AppTab.Settings -> stringResource(Res.string.tab_settings)
                            }
                            NavigationBarItem(selected = tab == item, onClick = { selectTab(item) },
                                modifier = Modifier.testTag("nav_${item.name.lowercase()}"),
                                icon = { Icon(when (item) {
                                    AppTab.Preview -> Icons.Outlined.Monitor
                                    AppTab.Devices -> Icons.Outlined.Usb
                                    AppTab.Input -> Icons.Outlined.Keyboard
                                    AppTab.Settings -> Icons.Outlined.Settings
                                }, null) }, label = { Text(title) })
                        }
                    }
                }
            }
        }
        if (inputPanel) ModalBottomSheet(onDismissRequest = { inputPanel = false }) {
            InputPage(inputState, inputActions, Modifier.fillMaxHeight(0.75f))
        }
        if (help) ConnectionGuide { help = false }
        if (diagnosticsOpen) {
            AlertDialog(onDismissRequest = ::closeDiagnostics,
                title = { Text(stringResource(Res.string.capture_diagnostics)) },
                text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(Res.string.capture_diagnostics_hint))
                    Spacer(Modifier.height(12.dp))
                    Text(diagnosticReport ?: stringResource(Res.string.capture_diagnostics_loading), fontSize = 11.sp)
                } },
                confirmButton = { TextButton(enabled = diagnosticReport != null,
                    onClick = { diagnosticReport?.let { clipboard.setText(AnnotatedString(it)) } }) {
                    Text(stringResource(Res.string.copy_diagnostics))
                } },
                dismissButton = { TextButton(onClick = ::closeDiagnostics) {
                    Text(stringResource(Res.string.close))
                } })
        }
    }
}

@Composable
private fun PreviewPane(state: CaptureState, fullscreen: Boolean, compact: Boolean,
    preferences: AppPreferences, onFullscreen: (Boolean) -> Unit, onConnect: (String?) -> Unit,
    onRefresh: () -> Unit, onDisconnect: () -> Unit, onAppSettings: () -> Unit,
    onDevices: () -> Unit, onInput: () -> Unit, preview: @Composable (Modifier) -> Unit) {
    var rotation by rememberSaveable { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize().padding(if (fullscreen) 0.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 16.dp)) {
        Box(Modifier.fillMaxWidth().then(
            if (fullscreen || compact) Modifier.weight(1f) else Modifier.aspectRatio(1.3f)
        ).clip(RoundedCornerShape(if (fullscreen) 0.dp else 22.dp)).background(Color.Black)
            .border(1.dp, Color(0xFF2C373D), RoundedCornerShape(if (fullscreen) 0.dp else 22.dp))) {
            // Always keep a real surface mounted, even while an empty-state overlay is visible.
            BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))
                .pointerInput(rotation, state.activeMode) {
                    detectTransformGestures { _, delta, scale, _ ->
                        zoom = (zoom * scale).coerceIn(1f, 4f)
                        pan = if (zoom == 1f) Offset.Zero else Offset(
                            (pan.x + delta.x).coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2),
                            (pan.y + delta.y).coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2))
                    }
                }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) },
                contentAlignment = Alignment.Center) {
                val mode = state.activeMode
                val extent = fittedVideoExtent(maxWidth.value, maxHeight.value,
                    mode?.width ?: 16, mode?.height ?: 9, rotation)
                preview(Modifier.requiredSize(extent.width.dp, extent.height.dp).graphicsLayer {
                    rotationZ = rotation.toFloat()
                    scaleX = zoom; scaleY = zoom
                    translationX = pan.x; translationY = pan.y
                })
            }
            if (state.phase != CapturePhase.Streaming) {
                Column(Modifier.fillMaxSize().background(Color(0xFF080D10)).padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.busy || state.phase == CapturePhase.WaitingForFrames) {
                        CircularProgressIndicator(Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Cable, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(phaseTitle(state), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F5F6), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(state.message?.let { captureMessageText(it, state.recoveryAttempt) } ?: phaseHint(state), color = Color(0xFFB2C0C7), fontSize = 12.sp,
                        textAlign = TextAlign.Center, lineHeight = 19.sp)
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(14.dp).clip(CircleShape)
                .background(Color(0xC020292E)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(if (state.phase == CapturePhase.Streaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                Text(if (state.phase == CapturePhase.Streaming) stringResource(Res.string.live_video) else "HDMI IN", fontSize = 10.sp, color = Color.White)
            }
            if (state.phase == CapturePhase.Streaming && preferences.showFrameStats) {
                Text("${state.framesPerSecond} fps · ${state.activeMode?.width} × ${state.activeMode?.height}",
                    Modifier.align(Alignment.BottomStart).padding(14.dp).background(Color(0xB3101619), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, fontSize = 11.sp)
            }
            FilledTonalIconButton(onClick = onInput,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("open_input")) {
                Icon(Icons.Outlined.Keyboard, stringResource(Res.string.tab_input))
            }
            if (fullscreen) {
                FilledTonalIconButton(onClick = { onFullscreen(false) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("exit_fullscreen")) {
                    Icon(Icons.Outlined.FullscreenExit, stringResource(Res.string.exit_fullscreen))
                }
            }
        }
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tool(Icons.Outlined.ZoomOutMap, stringResource(Res.string.reset)) { zoom = 1f; pan = Offset.Zero; rotation = 0 }
                Tool(Icons.Outlined.Rotate90DegreesCcw, stringResource(Res.string.rotate)) { rotation = (rotation + 90) % 360; zoom = 1f; pan = Offset.Zero }
                Tool(Icons.Outlined.Tune, stringResource(Res.string.video_settings)) { onDevices() }
                Tool(Icons.Outlined.Fullscreen, stringResource(Res.string.fullscreen)) { onFullscreen(true) }
            }
            if (!compact) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DevicePanel(state, onConnect, onRefresh, onDisconnect, onAppSettings)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Step("01", stringResource(Res.string.step_source), stringResource(Res.string.step_source_hint), Modifier.weight(1f))
                        Step("02", stringResource(Res.string.step_capture), stringResource(Res.string.step_capture_hint), Modifier.weight(1f))
                        Step("03", stringResource(Res.string.step_permission), stringResource(Res.string.step_permission_hint), Modifier.weight(1f))
                    }
                    Text(stringResource(Res.string.local_preview), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun DevicePanel(state: CaptureState, onConnect: (String?) -> Unit, onRefresh: () -> Unit,
    onDisconnect: () -> Unit, onAppSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Usb, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.selectedDevice?.let { deviceDisplayName(it) } ?: stringResource(Res.string.usb_video_input), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(if (state.devices.isEmpty()) stringResource(Res.string.waiting_for_card) else stringResource(Res.string.devices_found, state.devices.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, stringResource(Res.string.refresh_devices), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (!state.usbHostSupported) Text(stringResource(Res.string.usb_host_unsupported), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        if (state.phase == CapturePhase.Streaming || state.phase == CapturePhase.WaitingForFrames) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.stop_preview)) }
        } else {
            Button(onClick = { onConnect(state.selectedDeviceId ?: state.devices.singleOrNull()?.id) },
                enabled = !state.busy && state.usbHostSupported,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("connect"), shape = RoundedCornerShape(12.dp)) {
                Text(when {
                    state.busy -> stringResource(Res.string.connecting)
                    !state.cameraPermission -> stringResource(Res.string.allow_video)
                    state.phase == CapturePhase.Error || state.phase == CapturePhase.Stalled -> stringResource(Res.string.reconnect)
                    state.devices.isEmpty() -> stringResource(Res.string.find_card)
                    state.devices.size > 1 && state.selectedDevice == null -> stringResource(Res.string.choose_device)
                    else -> stringResource(Res.string.connect_card)
                }, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!state.cameraPermission) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.camera_permission_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onAppSettings) { Text(stringResource(Res.string.system_settings), fontSize = 11.sp) }
        }
    }
}

@Composable private fun Tool(icon: ImageVector, label: String, action: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(23.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Step(number: String, title: String, subtitle: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(number, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun phaseTitle(state: CaptureState): String = when (state.phase) {
    CapturePhase.Idle -> stringResource(Res.string.phase_idle)
    CapturePhase.Permission -> stringResource(Res.string.phase_permission)
    CapturePhase.Connecting -> stringResource(Res.string.phase_connecting)
    CapturePhase.WaitingForFrames -> stringResource(Res.string.phase_waiting)
    CapturePhase.Streaming -> stringResource(Res.string.live_video)
    CapturePhase.Stalled -> stringResource(Res.string.phase_stalled)
    CapturePhase.Error -> stringResource(Res.string.phase_error)
    CapturePhase.Paused -> stringResource(Res.string.phase_paused)
}
@Composable
internal fun phaseHint(state: CaptureState): String = when (state.phase) {
    CapturePhase.Permission -> stringResource(Res.string.hint_permission)
    CapturePhase.Connecting -> stringResource(Res.string.hint_connecting)
    CapturePhase.WaitingForFrames -> stringResource(Res.string.hint_waiting)
    else -> stringResource(Res.string.hint_idle)
}

@Composable
internal fun deviceDisplayName(device: CaptureDevice): String =
    device.name.ifBlank { stringResource(Res.string.generic_device) }

@Composable
internal fun captureMessageText(message: CaptureMessage, attempt: Int): String = when (message) {
    CaptureMessage.Unplugged -> stringResource(Res.string.msg_unplugged)
    CaptureMessage.ResumeOnReturn -> stringResource(Res.string.msg_resume)
    CaptureMessage.CameraPermissionRequired -> stringResource(Res.string.msg_camera_permission)
    CaptureMessage.DeviceMissing -> stringResource(Res.string.msg_device_missing)
    CaptureMessage.Recovering -> stringResource(Res.string.msg_recovering, attempt)
    CaptureMessage.Disconnected -> stringResource(Res.string.msg_disconnected)
    CaptureMessage.UsbClosed -> stringResource(Res.string.msg_usb_closed)
    CaptureMessage.UsbPermissionDenied -> stringResource(Res.string.msg_usb_denied)
    CaptureMessage.UsbOpenFailed -> stringResource(Res.string.msg_usb_open)
    CaptureMessage.UsbPermissionFailed -> stringResource(Res.string.msg_usb_permission)
    CaptureMessage.Stopped -> stringResource(Res.string.msg_stopped)
    CaptureMessage.VideoStartFailed -> stringResource(Res.string.msg_video_start)
    CaptureMessage.NativeUnavailable -> stringResource(Res.string.msg_native_unavailable)
    CaptureMessage.NoFrames -> stringResource(Res.string.msg_no_frames)
}
