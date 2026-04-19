import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class WindowSettings(private val file: File, val defaultSize: DpSize = DpSize(800.dp, 1200.dp)) {
    fun load(): WindowStateData {
        val props = Properties()
        if (file.exists()) {
            try {
                FileInputStream(file).use { props.load(it) }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val width = props.getProperty("width", "${defaultSize.width}").toInt()
        val height = props.getProperty("height", "${defaultSize.height}").toInt()
        val x = props.getProperty("x", "-1").toInt()
        val y = props.getProperty("y", "-1").toInt()

        val position = if (x != -1 && y != -1 && isPointOnScreen(x, y)) {
            WindowPosition(x.dp, y.dp)
        } else {
            WindowPosition(Alignment.Companion.Center)
        }

        return WindowStateData(DpSize(width.dp, height.dp), position)
    }

    fun save(width: Int, height: Int, x: Int, y: Int) {
        val props = Properties()
        props.setProperty("width", width.toString())
        props.setProperty("height", height.toString())
        props.setProperty("x", x.toString())
        props.setProperty("y", y.toString())
        try {
            FileOutputStream(file).use { props.store(it, "Window Settings") }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun isPointOnScreen(x: Int, y: Int): Boolean {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val screens = ge.screenDevices
        for (screen in screens) {
            if (screen.defaultConfiguration.bounds.contains(x, y)) {
                return true
            }
        }
        return false
    }
}

data class WindowStateData(val size: DpSize, val position: WindowPosition)