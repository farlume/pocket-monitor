package dev.icelum.pocketmonitor

import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var capture: UvcCaptureController
    private lateinit var input: BluetoothInputController
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestInput() else input.refresh()
    }
    private val bluetoothEnable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) input.enable() else input.refresh()
    }
    private val discoverablePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) makeDiscoverable()
    }
    private val discoverable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { input.refresh() }
    private var pendingDevice: String? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        capture.updatePermission()
        if (granted) connectGranted(pendingDevice) else capture.permissionDenied()
        pendingDevice = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        capture = UvcCaptureController(applicationContext)
        input = BluetoothInputController({ inputAvailability(applicationContext) }, {
            if (Build.VERSION.SDK_INT >= 28) AndroidHidTransport(applicationContext)
            else error("Bluetooth HID requires Android 9")
        })
        val inputActions = InputActions(
            enable = ::requestInput, disable = input::disable,
            settings = {
                if (inputAvailability(this) == InputPhase.PermissionRequired) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }, discoverable = ::makeDiscoverable, refresh = input::refresh, connect = input::connect,
            key = { input.key(it) }, text = input::text, move = input::move,
            click = { input.click(it) }, cancel = input::cancelSending,
            keyDown = { input.keyDown(it) }, keyUp = { input.keyUp(it) },
        )
        val preferenceStore = AppPreferencesStore(applicationContext)
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            val state by capture.state.collectAsStateWithLifecycle()
            val inputState by input.state.collectAsStateWithLifecycle()
            var preferences by remember { mutableStateOf(preferenceStore.read()) }
            var tab by rememberSaveable { mutableStateOf(AppTab.Preview) }
            var fullscreen by rememberSaveable { mutableStateOf(false) }
            var showLicenses by rememberSaveable { mutableStateOf(false) }
            var licenseDocuments by remember { mutableStateOf<List<LicenseDocument>?>(null) }
            var licenseFailed by remember { mutableStateOf(false) }
            val dark = preferences.theme.isDark(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            BackHandler(!fullscreen && tab != AppTab.Preview) { tab = AppTab.Preview }
            BackHandler(fullscreen) { fullscreen = false }
            LaunchedEffect(fullscreen) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
                }
            }
            LaunchedEffect(state.phase, inputState.phase, preferences.keepScreenOn) {
                if (preferences.keepScreenOn && (inputState.phase == InputPhase.Connected || state.phase in setOf(CapturePhase.Connecting, CapturePhase.WaitingForFrames, CapturePhase.Streaming, CapturePhase.Stalled))) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            LaunchedEffect(showLicenses) {
                if (showLicenses && licenseDocuments == null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            assets.list("licenses").orEmpty().sorted().map { name ->
                                LicenseDocument(name, assets.open("licenses/$name").bufferedReader().use { it.readText() })
                            }.also { check(it.isNotEmpty()) }
                        }
                    }
                    licenseDocuments = result.getOrNull()
                    licenseFailed = result.isFailure
                }
            }
            AppLocale(preferences.language) {
                MonitorScreen(state, fullscreen, { fullscreen = it }, ::requestConnection,
                    capture::refreshDevices, capture::userDisconnect, capture::selectMode,
                    onAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    preview = { modifier -> CapturePreview(modifier, capture) },
                    preferences = preferences,
                    onPreferences = { preferenceStore.write(it); preferences = it },
                    selectedTab = tab, onTab = { tab = it }, appVersion = appVersion,
                    onOpenLicenses = { showLicenses = true }, inputState = inputState, inputActions = inputActions,
                    onDiagnostics = capture::diagnosticsWithNativeLogs)
                if (showLicenses) MonitorTheme(preferences) {
                    LicenseDialog(licenseDocuments, licenseFailed) { showLicenses = false }
                }
            }
        }
    }

    private fun requestConnection(id: String?) {
        capture.updatePermission()
        if (!capture.state.value.cameraPermission) {
            pendingDevice = id
            permission.launch(Manifest.permission.CAMERA)
        } else connectGranted(id)
    }

    private fun connectGranted(id: String?) {
        capture.refreshDevices()
        val device = connectionDevice(capture.state.value.devices, id)
        if (device != null) capture.connect(device.id)
    }

    private fun requestInput() {
        when (inputAvailability(this)) {
            InputPhase.PermissionRequired -> if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            InputPhase.BluetoothOff -> runCatching { bluetoothEnable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { input.refresh() }
            else -> input.enable()
        }
    }

    private fun makeDiscoverable() {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            discoverablePermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
            return
        }
        runCatching { discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)) }.onFailure { input.refresh() }
    }

    override fun onStart() { super.onStart(); capture.start(); input.start() }
    override fun onStop() { input.stop(); capture.stop(); super.onStop() }
    override fun onDestroy() { input.stop(); capture.destroy(); super.onDestroy() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); capture.usbAccessChanged() }
}

@Composable
private fun CapturePreview(modifier: Modifier, capture: UvcCaptureController) {
    AndroidView(modifier = modifier, factory = { context ->
        TextureView(context).apply {
            isOpaque = true
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                private var output: Surface? = null
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    output = Surface(texture).also(capture::attachSurface)
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    val old = output
                    output = null
                    if (old != null) {
                        capture.detachSurface(old) { texture.release() }
                        return false
                    }
                    return true
                }
            }
        }
    })
}
