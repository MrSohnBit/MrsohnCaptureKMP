import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
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
import java.awt.Toolkit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.system.exitProcess
import org.jetbrains.skia.Image as SkiaImage

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
                currentSavePath
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
            onSettingsChanged = { adb, save ->
                currentAdbPath = adb
                currentSavePath = save
            },
            onExit = saveAndExit
        )
    }
}

@Composable
fun MrSohnCaptureApp(
    initialAdbPath: String,
    initialSavePath: String,
    onSettingsChanged: (String, String) -> Unit,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // 설정 값들
    var adbPath by remember { mutableStateOf(initialAdbPath) }
    var savePath by remember { mutableStateOf(initialSavePath) }
    
    val adbRunner = remember(adbPath) { AdbRunner(adbPath.takeIf { it.isNotBlank() }) }

    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentlyDisplayedFile by remember { mutableStateOf<File?>(null) }
    var capturedImages by remember { mutableStateOf(listOf<File>()) }
    var isCapturing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }
    var showFlash by remember { mutableStateOf(false) }

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

    fun refreshCapturedImages() {
        capturedImages = saveDir.listFiles { _, name -> name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() }
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
                    statusMessage = "Viewing: ${file.name}"
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
        if (selectedDevice != null) {
            scope.launch {
                isCapturing = true
                statusMessage = "Capturing ${selectedDevice?.model}..."
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(saveDir, "capture_$timestamp.png")

                val success = withContext(Dispatchers.IO) {
                    adbRunner.captureScreen(selectedDevice?.id, file)
                }

                if (success) {
                    refreshCapturedImages()
                    val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                    currentImage = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                    currentlyDisplayedFile = file
                    statusMessage = "Saved to ${saveDir.name}"
                    showFlash = true
                } else {
                    statusMessage = "Capture failed. Check ADB path."
                }
                isCapturing = false
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
            }
            delay(1000)
        }
    }

    LaunchedEffect(adbRunner) {
        while(true) {
            val foundDevices = withContext(Dispatchers.IO) { adbRunner.getDevices() }
            devices = foundDevices
            if (selectedDevice == null && devices.isNotEmpty()) {
                selectedDevice = devices.first()
            } else if (selectedDevice != null && !devices.any { it.id == selectedDevice?.id }) {
                selectedDevice = if (devices.isNotEmpty()) devices.first() else null
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

    var showSettings by remember { mutableStateOf(false) }

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
                        devices = devices,
                        selectedDevice = selectedDevice,
                        onDeviceSelected = { selectedDevice = it },
                        onOpenGallery = {
                            try {
                                java.awt.Desktop.getDesktop().open(saveDir)
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        onEdit = onEdit,
                        onOpenSettings = { showSettings = true },
                        isEditEnabled = currentlyDisplayedFile != null
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderArea(statusMessage)
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
    devices: List<DeviceInfo>,
    selectedDevice: DeviceInfo?,
    onDeviceSelected: (DeviceInfo) -> Unit,
    onOpenGallery: () -> Unit,
    onEdit: () -> Unit,
    onOpenSettings: () -> Unit,
    isEditEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Devices", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        devices.forEach { device ->
            val isSelected = selectedDevice?.id == device.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                    .clickable { onDeviceSelected(device) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFF7AF39) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    device.model,
                    color = if (isSelected) Color.White else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }

        if (devices.isEmpty()) {
            Text("No devices found", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onEdit,
            enabled = isEditEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.1f),
                disabledContainerColor = Color.White.copy(alpha = 0.05f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Rounded.Edit, 
                contentDescription = null, 
                modifier = Modifier.size(18.dp),
                tint = if (isEditEnabled) Color.White else Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit", fontSize = 14.sp, color = if (isEditEnabled) Color.White else Color.Gray)
        }

        Spacer(modifier = Modifier.height(8.dp))
        

        Button(
            onClick = onOpenGallery,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gallery", fontSize = 14.sp, color = Color.White)
        }

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", fontSize = 14.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))
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
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
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
fun HeaderArea(status: String) {
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
                "MrSohn Capture",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(status, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
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
