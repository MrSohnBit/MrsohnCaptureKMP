import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mrsohn.capture.adb.AdbRunner
import mrsohn.capture.adb.DeviceInfo
import mrsohn.capture.ui.theme.MrSohnCaptureTheme
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.beans.PropertyChangeListener
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

/**
 * windows
 * .\gradlew.bat :app:packageDistributionForCurrentOS
 * .\gradlew.bat :app:createDistributable
 */
fun main() = application {
    val settingsFile = File(System.getProperty("user.home"), ".mrsohn_capture_settings.properties")
    val settings = WindowSettings(settingsFile)
    val savedState = settings.load()

    val windowState = rememberWindowState(
        position = savedState.position,
        size = savedState.size
    )

    // 가변적인 설정을 관리하기 위한 State (저장 경로 및 ADB 경로)
    var currentAdbPath by mutableStateOf(savedState.adbPath)
    var currentSavePath by mutableStateOf(savedState.savePath)
    var isSidebarExpanded by mutableStateOf(savedState.isSidebarExpanded)

    val saveAndExit = {
        try {
            val x = if (windowState.position.x.isSpecified) windowState.position.x.value.toInt() else -1
            val y = if (windowState.position.y.isSpecified) windowState.position.y.value.toInt() else -1
            settings.save(
                windowState.size.width.value.toInt(),
                windowState.size.height.value.toInt(),
                x,
                y,
                currentAdbPath,
                currentSavePath,
                isSidebarExpanded
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        exitApplication()
    }

    Window(
        onCloseRequest = saveAndExit,
        title = "MrSohn Capture",
        state = windowState
    ) {
        MrSohnCaptureApp(
            initialAdbPath = currentAdbPath,
            initialSavePath = currentSavePath,
            initialSidebarExpanded = isSidebarExpanded,
            onSettingsChanged = { adb, save ->
                currentAdbPath = adb
                currentSavePath = save
            },
            onSidebarExpandedChanged = { isSidebarExpanded = it },
            onExit = saveAndExit
        )
    }
}

@Composable
fun MrSohnCaptureApp(
    initialAdbPath: String,
    initialSavePath: String,
    initialSidebarExpanded: Boolean,
    onSettingsChanged: (String, String) -> Unit,
    onSidebarExpandedChanged: (Boolean) -> Unit,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // 설정 값들
    var adbPath by remember { mutableStateOf(initialAdbPath) }
    var savePath by remember { mutableStateOf(initialSavePath) }
    
    val adbRunner = remember(adbPath) { AdbRunner(adbPath.takeIf { it.isNotBlank() }) }
    var isAdbValid by remember { mutableStateOf(true) }

    // 자동 ADB 경로 설정 및 저장
    LaunchedEffect(Unit) {
        if (adbPath.isBlank()) {
            val autoPath = adbRunner.adbPath
            if (File(autoPath).exists()) {
                adbPath = autoPath
                onSettingsChanged(autoPath, savePath)
            }
        }
    }

    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentlyDisplayedFile by remember { mutableStateOf<File?>(null) }
    var capturedImages by remember { mutableStateOf(listOf<File>()) }
    var isCapturing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }
    var showFlash by remember { mutableStateOf(false) }
    var isSidebarExpanded by remember { mutableStateOf(initialSidebarExpanded) }

    val currentImageFile: File? = currentlyDisplayedFile.takeIf { it != null }

    val saveDir = remember(savePath) {
        val dir = if (savePath.isNotBlank()) {
            File(savePath)
        } else {
            val picturesDir = File(System.getProperty("user.home"), "Pictures")
            File(picturesDir, "MrSohnCapture")
        }
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun File.creationTimestamp(): Long = runCatching {
        Files.readAttributes(toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
    }.getOrElse { lastModified() }

    fun refreshCapturedImages() {
        capturedImages = saveDir.listFiles { _, name -> name.endsWith(".png") }
            ?.sortedByDescending { it.creationTimestamp() }
            ?: emptyList()

        if (currentlyDisplayedFile != null && !currentlyDisplayedFile!!.exists()) {
            currentlyDisplayedFile = null
            currentImage = null
            statusMessage = "Selected file was removed"
        }
    }

    fun showFile(file: File) {
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                val skiaImage = SkiaImage.makeFromEncoded(bytes)
                if (skiaImage != null) {
                    currentImage = skiaImage.toComposeImageBitmap()
                    currentlyDisplayedFile = file
                    statusMessage = "${file.name}"
                } else {
                    statusMessage = "Failed to decode image"
                }
            } catch (e: Exception) {
                statusMessage = "Error loading image"
            }
        }
    }

    fun reloadCurrentFile(preserveStatusMessage: Boolean = true) {
        val file = currentlyDisplayedFile ?: return
        if (!file.exists()) {
            currentImage = null
            currentlyDisplayedFile = null
            statusMessage = "Selected file was removed"
            return
        }

        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                val skiaImage = SkiaImage.makeFromEncoded(bytes)
                if (skiaImage != null) {
                    currentImage = skiaImage.toComposeImageBitmap()
                    currentlyDisplayedFile = file
                    if (!preserveStatusMessage) {
                        statusMessage = "${file.name}"
                    }
                } else {
                    statusMessage = "Failed to decode image"
                }
            } catch (e: Exception) {
                statusMessage = "Error loading image"
            }
        }
    }

    fun showAdjacentFile(direction: Int) {
        val files = capturedImages
        if (files.isEmpty()) return

        val currentIdx = currentlyDisplayedFile?.let { current ->
            files.indexOfFirst { it.absolutePath == current.absolutePath }
        } ?: -1

        val nextIndex = when {
            currentIdx == -1 -> if (direction > 0) 0 else files.lastIndex
            else -> (currentIdx + direction).coerceIn(0, files.lastIndex)
        }

        showFile(files[nextIndex])
    }

    fun deleteFile() {
        val files = capturedImages
        val currentIdx = currentlyDisplayedFile?.let { current ->
            files.indexOfFirst { it.absolutePath == current.absolutePath }
        } ?: -1
        val file = files.getOrNull(currentIdx) ?: return
        scope.launch {
            try {
                if (file.delete()) {
                    refreshCapturedImages()
                    statusMessage = "Deleted: ${file.name}"
                    val remainingFiles = files.filter { it.absolutePath != file.absolutePath }
                    if (remainingFiles.isNotEmpty()) {
                        val nextIdx = currentIdx.coerceIn(0, remainingFiles.lastIndex)
                        showFile(remainingFiles[nextIdx])
                    } else {
                        currentImage = null
                        currentlyDisplayedFile = null
                    }
                } else {
                    statusMessage = "Failed to delete ${file.name}"
                }
            } catch (e: Exception) {
                statusMessage = "Error deleting file"
            }
        }
    }

    fun clipboardCopy() {
        val files = capturedImages
        val currentIdx = currentlyDisplayedFile?.let { current ->
            files.indexOfFirst { it.absolutePath == current.absolutePath }
        } ?: -1
        val file = files.getOrNull(currentIdx) ?: return

        scope.launch {
            try {
                val image = withContext(Dispatchers.IO) { ImageIO.read(file) } ?: return@launch
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(ImageSelection(image), null)
                statusMessage = "Copied to clipboard: ${file.name}"
            } catch (e: Exception) {
                statusMessage = "Error copying to clipboard"
            }
        }
    }

    val performCapture = {
        if (selectedDevice != null && !isCapturing) {
            isCapturing = true
            scope.launch {
                try {
                    statusMessage = "Capturing ${selectedDevice?.model}..."
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                    val file = File(saveDir, "capture_$timestamp.png")

                    val success = withContext(Dispatchers.IO) {
                        adbRunner.captureScreen(selectedDevice?.id, file)
                    }

                    if (success && file.exists()) {
                        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                        if (bytes.isNotEmpty()) {
                            val skiaImage = try {
                                SkiaImage.makeFromEncoded(bytes)
                            } catch (e: Exception) {
                                null
                            }
                            
                            if (skiaImage != null) {
                                currentImage = skiaImage.toComposeImageBitmap()
                                currentlyDisplayedFile = file
                                refreshCapturedImages()
                                statusMessage = "Saved to ${saveDir.name}"
                                showFlash = true
                            } else {
                                statusMessage = "Failed to decode image"
                            }
                        }
                    } else {
                        statusMessage = "Capture failed. Check ADB path."
                    }
                } catch (e: Exception) {
                    statusMessage = "Error: ${e.message}"
                } finally {
                    isCapturing = false
                }
            }
        }
        Unit
    }

    fun handleShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        return when {
            event.key == Key.Spacebar -> {
                performCapture()
                true
            }
            event.key == Key.DirectionLeft -> {
                showAdjacentFile(-1)
                true
            }
            event.key == Key.DirectionRight -> {
                showAdjacentFile(1)
                true
            }
            event.key == Key.W && (event.isMetaPressed || event.isCtrlPressed) -> {
                onExit()
                true
            }
            event.key == Key.F4 && event.isAltPressed -> {
                onExit()
                true
            }
            event.key == Key.Delete -> {
                deleteFile()
                true
            }
            event.key == Key.C && (event.isMetaPressed || event.isCtrlPressed) -> {
                clipboardCopy()
                true
            }
            else -> false
        }
    }

    LaunchedEffect(Unit) {
        refreshCapturedImages()
    }

    LaunchedEffect(saveDir) {
        var lastSnapshot = emptySet<String>()
        while (true) {
            val currentSnapshot = withContext(Dispatchers.IO) {
                saveDir.listFiles { _, name -> name.endsWith(".png") }
                    ?.map { "${it.name}:${it.lastModified()}:${it.length()}" }
                    ?.toSet()
                    ?: emptySet()
            }
            if (currentSnapshot != lastSnapshot) {
                lastSnapshot = currentSnapshot
                refreshCapturedImages()
                reloadCurrentFile()
            }
            delay(1000)
        }
    }

    LaunchedEffect(adbRunner) {
        while(true) {
            val available = withContext(Dispatchers.IO) { adbRunner.isAdbAvailable() }
            isAdbValid = available
            
            if (available) {
                val foundDevices = withContext(Dispatchers.IO) { adbRunner.getDevices() }
                devices = foundDevices
                if (selectedDevice == null && devices.isNotEmpty()) {
                    selectedDevice = devices.first()
                } else if (selectedDevice != null && !devices.any { it.id == selectedDevice?.id }) {
                    selectedDevice = if (devices.isNotEmpty()) devices.first() else null
                }
            } else {
                devices = emptyList()
                selectedDevice = null
                statusMessage = "ADB not found. Please check ADB path in Settings."
            }
            delay(5000)
        }
    }

    val onEdit = {
        currentlyDisplayedFile?.let { file ->
            scope.launch(Dispatchers.IO) {
                try {
                    val os = System.getProperty("os.name").lowercase()
                    when {
                        os.contains("win") -> ProcessBuilder("mspaint", file.absolutePath).start()
                        os.contains("mac") -> ProcessBuilder("open", file.absolutePath).start()
                        else -> ProcessBuilder("xdg-open", file.absolutePath).start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        Unit
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DisposableEffect(Unit) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val listener = PropertyChangeListener { event ->
            if (event.propertyName == "activeWindow" && event.newValue != null) {
                focusRequester.requestFocus()
                reloadCurrentFile()
            }
        }

        focusManager.addPropertyChangeListener("activeWindow", listener)

        onDispose {
            focusManager.removePropertyChangeListener("activeWindow", listener)
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showShortcutHelp by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsDialog(
            initialAdbPath = adbPath,
            initialSavePath = savePath,
            onDismiss = { showSettings = false },
            onSave = { newAdbPath, newSavePath ->
                adbPath = newAdbPath
                savePath = newSavePath
                onSettingsChanged(newAdbPath, newSavePath)
                showSettings = false
            }
        )
    }

    if (showShortcutHelp) {
        ShortcutHelpDialog(onDismiss = { showShortcutHelp = false })
    }

    MrSohnCaptureTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusTarget()
                .onPreviewKeyEvent { handleShortcut(it) },
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F1B2C), Color(0xFF162A44))
                        )
                    )
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Sidebar(
                        isExpanded = isSidebarExpanded,
                        onToggle = { 
                            isSidebarExpanded = !isSidebarExpanded
                            onSidebarExpandedChanged(isSidebarExpanded)
                        },
                        onShowManual = { showShortcutHelp = true },
                        devices = devices,
                        selectedDevice = selectedDevice,
                        isAdbValid = isAdbValid,
                        onDeviceSelected = { selectedDevice = it },
                        onOpenGallery = {
                            val fileToSelect = currentlyDisplayedFile
                            try {
                                val os = System.getProperty("os.name").lowercase()
                                if (fileToSelect != null && fileToSelect.exists()) {
                                    if (os.contains("win")) {
                                        ProcessBuilder("explorer.exe", "/select,${fileToSelect.absolutePath}").start()
                                    } else if (os.contains("mac")) {
                                        ProcessBuilder("open", "-R", fileToSelect.absolutePath).start()
                                    } else {
                                        java.awt.Desktop.getDesktop().open(saveDir)
                                    }
                                } else {
                                    java.awt.Desktop.getDesktop().open(saveDir)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                try {
                                    java.awt.Desktop.getDesktop().open(saveDir)
                                } catch (e2: Exception) {
                                    e2.printStackTrace()
                                }
                            }
                        },
                        onEdit = onEdit,
                        onConnectWireless = {
                            selectedDevice?.let { device ->
                                scope.launch(Dispatchers.IO) {
                                    statusMessage = "Connecting to ${device.model} wirelessly..."
                                    val result = adbRunner.connectWireless(device.id)
                                    statusMessage = if (result != null) {
                                        "Connected to $result"
                                    } else {
                                        "Wireless connection failed"
                                    }
                                }
                            }
                        },
                        onDisconnectWireless = {
                            selectedDevice?.let { device ->
                                scope.launch(Dispatchers.IO) {
                                    statusMessage = "Disconnecting ${device.id}..."
                                    if (adbRunner.disconnect(device.id)) {
                                        statusMessage = "Disconnected ${device.id}"
                                        selectedDevice = null
                                    } else {
                                        statusMessage = "Failed to disconnect"
                                    }
                                }
                            }
                        },
                        onOpenSettings = { showSettings = true },
                        isEditEnabled = currentlyDisplayedFile != null,
                        isWirelessEnabled = selectedDevice != null && !selectedDevice!!.id.contains(":"),
                        isDisconnectEnabled = selectedDevice != null && selectedDevice!!.id.contains(":")
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderArea(
                            status = statusMessage
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp))
                                .clickable(enabled = selectedDevice != null) { performCapture() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentImage != null) {
                                Image(
                                    bitmap = currentImage!!,
                                    contentDescription = "Device Screen",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                EmptyPreview(selectedDevice != null)
                            }

                            if (showFlash) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
                                LaunchedEffect(Unit) {
                                    delay(100)
                                    showFlash = false
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        BottomControls(
                            currentImageFile = currentImageFile,
                            capturedImages = capturedImages,
                            onThumbnailClick = { showFile(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Sidebar(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onShowManual: () -> Unit,
    devices: List<DeviceInfo>,
    selectedDevice: DeviceInfo?,
    isAdbValid: Boolean,
    onDeviceSelected: (DeviceInfo) -> Unit,
    onOpenGallery: () -> Unit,
    onEdit: () -> Unit,
    onConnectWireless: () -> Unit,
    onDisconnectWireless: () -> Unit,
    onOpenSettings: () -> Unit,
    isEditEnabled: Boolean,
    isWirelessEnabled: Boolean,
    isDisconnectEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .width(if (isExpanded) 260.dp else 80.dp)
            .fillMaxHeight()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(if (isExpanded) 16.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar in Sidebar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = if (isExpanded) Arrangement.SpaceBetween else Arrangement.Center
        ) {

            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Rounded.ArrowCircleRight,
                    contentDescription = "Toggle Sidebar",
                    tint = Color.White,
                    modifier = Modifier.rotate(if (isExpanded) -180f else 0f)
                )
            }

            if (isExpanded) {
                Text(
                    "MrSohn Capture",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Devices", style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
        } else {
            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        devices.forEach { device ->
            val isSelected = selectedDevice?.id == device.id
            val isWireless = device.id.contains(":")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onDeviceSelected(device) }
                    .padding(if (isExpanded) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isWireless) Icons.Rounded.Wifi else Icons.Rounded.Usb,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFF7AF39) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                if (isExpanded) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        device.model,
                        color = if (isSelected) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
        }

        if (devices.isEmpty() && isExpanded) {
            Text(
                if (isAdbValid) "No devices found" else "ADB not found",
                color = if (isAdbValid) Color.Gray else Color(0xFFE57373),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        SidebarButton(
            onClick = onShowManual,
            enabled = true,
            isExpanded = isExpanded,
            icon = Icons.Rounded.HelpOutline,
            label = "Manual"
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!isDisconnectEnabled) {
            SidebarButton(
                onClick = onConnectWireless,
                enabled = isWirelessEnabled,
                isExpanded = isExpanded,
                icon = Icons.Rounded.Wifi,
                label = "Wireless",
                tint = if (isWirelessEnabled) Color.White else Color.Gray
            )
        }

        if (isDisconnectEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SidebarButton(
                onClick = onDisconnectWireless,
                enabled = true,
                isExpanded = isExpanded,
                icon = Icons.Rounded.WifiOff,
                label = "Disconnect",
                containerColor = Color(0xFFE57373).copy(alpha = 0.2f),
                contentColor = Color(0xFFE57373)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SidebarButton(
            onClick = onEdit,
            enabled = isEditEnabled,
            isExpanded = isExpanded,
            icon = Icons.Rounded.Edit,
            label = "Edit",
            tint = if (isEditEnabled) Color.White else Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        SidebarButton(
            onClick = onOpenGallery,
            enabled = true,
            isExpanded = isExpanded,
            icon = Icons.Rounded.Collections,
            label = "Gallery"
        )

        Spacer(modifier = Modifier.height(8.dp))

        SidebarButton(
            onClick = onOpenSettings,
            enabled = true,
            isExpanded = isExpanded,
            icon = Icons.Rounded.Settings,
            label = "Settings"
        )
    }
}

@Composable
fun SidebarButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isExpanded: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    containerColor: Color = Color.White.copy(alpha = 0.1f),
    contentColor: Color = Color.White
) {
    if (isExpanded) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = Color.White.copy(alpha = 0.05f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) tint else Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = if (enabled) contentColor else Color.Gray)
        }
    } else {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) containerColor else Color.White.copy(alpha = 0.05f))
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) tint else Color.Gray
            )
        }
    }
}

@Composable
fun SettingsDialog(
    initialAdbPath: String,
    initialSavePath: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var adbPath by remember { mutableStateOf(initialAdbPath) }
    var savePath by remember { mutableStateOf(initialSavePath) }
    
    // 실시간 ADB 경로 유효성 체크
    val isAdbPathValid by produceState(initialValue = true, adbPath) {
        value = withContext(Dispatchers.IO) {
            AdbRunner(adbPath.takeIf { it.isNotBlank() }).isAdbAvailable()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = Color.White) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("ADB Path (platform-tools)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = adbPath,
                    onValueChange = { adbPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. /path/to/adb") },
                    singleLine = true,
                    isError = !isAdbPathValid,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorContainerColor = Color(0xFFE57373).copy(alpha = 0.1f)
                    )
                )
                if (!isAdbPathValid) {
                    Text(
                        "ADB executable not found at this path",
                        color = Color(0xFFE57373),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Save Directory (PC)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = savePath,
                    onValueChange = { savePath = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. /Users/name/Pictures/Captures") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(adbPath, savePath) }) {
                Text("Save", color = Color(0xFFF7AF39))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF162A44),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun HeaderArea(
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                status,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun ShortcutHelpDialog(onDismiss: () -> Unit) {
    val isMac = remember {
        System.getProperty("os.name")
            .lowercase(Locale.getDefault())
            .contains("mac")
    }

    val copyShortcut = if (isMac) {
        "Ctrl+C 또는 Cmd+C"
    } else {
        "Ctrl+C"
    }

    val exitShortcut = if (isMac) {
        "Ctrl+W 또는 Cmd+W"
    } else {
        "Ctrl+W"
    }

    val shortcuts = listOf(
        "Space" to "현재 선택된 기기 화면 캡처",
        "← / →" to "이전 / 다음 이미지 보기",
        "Delete" to "현재 이미지 삭제",
        copyShortcut to "현재 이미지 클립보드 복사",
        exitShortcut to "앱 종료",
        "Alt+F4" to "앱 종료"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = {
            Text("단축키 안내")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shortcuts.forEach { (shortcut, description) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = shortcut,
                            modifier = Modifier.width(150.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun EmptyPreview(hasDevice: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            if (hasDevice) Icons.Rounded.Screenshot else Icons.Rounded.UsbOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (hasDevice) "Click to Capture" else "No Device Selected",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun BottomControls(
    currentImageFile: File?,
    capturedImages: List<File>,
    onThumbnailClick: (File) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentImageFile) {
        val index = capturedImages.indexOfFirst { it.absolutePath == currentImageFile?.absolutePath }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            items(capturedImages) { file ->
                ThumbnailItem(
                    file = file,
                    isSelected = file.absolutePath == currentImageFile?.absolutePath,
                    onClick = onThumbnailClick
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun ThumbnailItem(file: File, isSelected: Boolean = false, onClick: (File) -> Unit) {
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                val skiaImage = SkiaImage.makeFromEncoded(bytes)
                if (skiaImage != null) {
                    bitmap = skiaImage.toComposeImageBitmap()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(if (isSelected) 3.dp else 0.dp, Color.Red.copy(alpha = if (isSelected) 1f else 0.1f), RoundedCornerShape(16.dp))
            .clickable { onClick(file) },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}


@Preview
@Composable
fun ThumbnailItemPreview() {
    ThumbnailItem(file = File("test.jpg"), isSelected = true) {}
}
