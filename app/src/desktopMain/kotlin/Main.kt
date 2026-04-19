import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mrsohn.capture.adb.AdbRunner
import mrsohn.capture.adb.DeviceInfo
import mrsohn.capture.ui.theme.MrSohnCaptureTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.jetbrains.skia.Image as SkiaImage


fun main() = application {
    val settingsFile = File("window_settings.properties")
    val settings = WindowSettings(settingsFile)
    val savedState = settings.load()
    
    val windowState = rememberWindowState(
        position = savedState.position,
        size = savedState.size
    )

    Window(
        onCloseRequest = {
            settings.save(
                windowState.size.width.value.toInt(),
                windowState.size.height.value.toInt(),
                windowState.position.x.value.toInt(),
                windowState.position.y.value.toInt()
            )
            exitApplication()
        },
        title = "MrSohn Capture",
        state = windowState
    ) {
        MrSohnCaptureApp()
    }
}

@Composable
fun MrSohnCaptureApp() {
    val adbRunner = remember { AdbRunner() }
    val scope = rememberCoroutineScope()
    
    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentlyDisplayedFile by remember { mutableStateOf<File?>(null) }
    var capturedImages by remember { mutableStateOf(listOf<File>()) }
    var isCapturing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }

    var showFlash by remember { mutableStateOf(false) }

    val saveDir = remember {
        val picturesDir = File(System.getProperty("user.home"), "Pictures")
        val dir = File(picturesDir, "MrSohnCapture")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    // Load existing captures
    LaunchedEffect(Unit) {
        val files = saveDir.listFiles { _, name -> name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        capturedImages = files
    }

    // Device discovery loop
    LaunchedEffect(Unit) {
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

    // Extracted Capture Logic
    val performCapture = {
        if (selectedDevice != null /*&& !isCapturing*/) {
            scope.launch {
                isCapturing = true
                statusMessage = "Capturing ${selectedDevice?.model}..."
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(saveDir, "capture_$timestamp.png")
                
                val success = withContext(Dispatchers.IO) {
                    adbRunner.captureScreen(selectedDevice?.id, file)
                }
                
                if (success) {
                    capturedImages = listOf(file) + (capturedImages.filter { it.absolutePath != file.absolutePath })
                    val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                    currentImage = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                    currentlyDisplayedFile = file
                    statusMessage = "Saved to Pictures/MrSohnCapture"
                    showFlash = true
                } else {
                    statusMessage = "Capture failed"
                }
                isCapturing = false
            }
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
    }

    MrSohnCaptureTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F1B2C),
                                Color(0xFF162A44)
                            )
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
                        onEdit = { onEdit() },
                        isEditEnabled = currentlyDisplayedFile != null
                    )

                    // Main Content Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderArea(statusMessage)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Preview Container (Now Clickable)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp))
                                .clickable(enabled = selectedDevice != null /*&& !isCapturing*/) { performCapture() },
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
                            
                            FocusBrackets(modifier = Modifier.size(120.dp))

                            if (showFlash) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.8f))
                                )
                                LaunchedEffect(Unit) {
                                    delay(100)
                                    showFlash = false
                                }
                            }

//                            if (isCapturing) {
//                                Box(
//                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
//                                    contentAlignment = Alignment.Center
//                                ) {
//                                    CircularProgressIndicator(color = Color(0xFFF7AF39))
//                                }
//                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Bottom Area: Recent Captures Only
                        BottomControls(
                            capturedImages = capturedImages,
                            onThumbnailClick = { file ->
                                scope.launch {
                                    try {
                                        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                                        currentImage = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                                        currentlyDisplayedFile = file
                                        statusMessage = "Viewing: ${file.name}"
                                    } catch (e: Exception) {
                                        statusMessage = "Error loading image"
                                    }
                                }
                            }
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
    }
}

@Composable
fun HeaderArea(status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "MrSohn Capture",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(status, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun FocusBrackets(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 2.dp.toPx()
            val bracketLengthPx = 20.dp.toPx()
            val color = Color.White.copy(alpha = 0.6f)
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            val topLeftPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, bracketLengthPx)
                lineTo(0f, 0f)
                lineTo(bracketLengthPx, 0f)
            }
            drawPath(path = topLeftPath, color = color, style = Stroke(strokeWidthPx))
            
            val topRightPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(canvasWidth - bracketLengthPx, 0f)
                lineTo(canvasWidth, 0f)
                lineTo(canvasWidth, bracketLengthPx)
            }
            drawPath(path = topRightPath, color = color, style = Stroke(strokeWidthPx))
            
            val bottomLeftPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, canvasHeight - bracketLengthPx)
                lineTo(0f, canvasHeight)
                lineTo(bracketLengthPx, canvasHeight)
            }
            drawPath(path = bottomLeftPath, color = color, style = Stroke(strokeWidthPx))
            
            val bottomRightPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(canvasWidth - bracketLengthPx, canvasHeight)
                lineTo(canvasWidth, canvasHeight)
                lineTo(canvasWidth, canvasHeight - bracketLengthPx)
            }
            drawPath(path = bottomRightPath, color = color, style = Stroke(strokeWidthPx))
        }
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
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
    capturedImages: List<File>,
    onThumbnailClick: (File) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            items(capturedImages.take(10)) { file ->
                ThumbnailItem(file, onThumbnailClick)
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun ThumbnailItem(file: File, onClick: (File) -> Unit) {
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
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
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Preview
@Composable
fun PreviewMrSohnCaptureApp() {
    MrSohnCaptureApp()
}
