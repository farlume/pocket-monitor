package dev.icelum.pocketmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icelum.pocketmonitor.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DevicePage(state: CaptureState, onConnect: (String) -> Unit, onRefresh: () -> Unit,
    onDisconnect: () -> Unit, onMode: (VideoMode) -> Unit, onDiagnostics: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.device_page_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.capture_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.devices.isEmpty()) Text(stringResource(Res.string.no_devices), fontSize = 13.sp)
        state.devices.forEachIndexed { index, device ->
            OutlinedButton(onClick = { onConnect(device.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(deviceDisplayName(device) + if (device.id == state.selectedDeviceId) stringResource(Res.string.selected_suffix) else "")
                    Text(stringResource(Res.string.device_number, index + 1) + " · " + device.id,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = onRefresh) { Text(stringResource(Res.string.refresh_devices)) }
        TextButton(onClick = onDiagnostics) { Text(stringResource(Res.string.capture_diagnostics)) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(Res.string.capture_format), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.format_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.modes.forEach { mode ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (mode == state.activeMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable(enabled = !state.busy) { onMode(mode) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mode.label, Modifier.weight(1f), fontSize = 13.sp)
                if (mode == state.activeMode) Icon(Icons.Outlined.Check, stringResource(Res.string.current_format), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.selectedDevice != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val device = state.selectedDevice!!
            Text("USB ID  ${device.vendorId.toString(16).padStart(4, '0')}:${device.productId.toString(16).padStart(4, '0')}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (state.selectedDevice != null) TextButton(onClick = onDisconnect) { Text(stringResource(Res.string.disconnect)) }
    }
}
