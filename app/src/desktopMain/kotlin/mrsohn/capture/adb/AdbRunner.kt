package mrsohn.capture.adb

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdbRunner(private val adbPath: String = "adb") {

    fun isAdbAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(adbPath, "version").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun getDevices(): List<DeviceInfo> {
        val devices = mutableListOf<DeviceInfo>()
        try {
            val process = ProcessBuilder(adbPath, "devices", "-l").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.contains("device product:")) {
                    val id = currentLine.substringBefore(" ").trim()
                    val model = currentLine.substringAfter("model:").substringBefore(" ").trim()
                    devices.add(DeviceInfo(id, model))
                } else if (currentLine.contains("device usb:")) {
                     val id = currentLine.substringBefore(" ").trim()
                     val model = currentLine.substringAfter("model:").substringBefore(" ").trim()
                     devices.add(DeviceInfo(id, model))
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return devices
    }

    fun captureScreen(deviceId: String?, targetFile: File): Boolean {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            // Use a directory that is likely to exist and be writable
            val tempDevicePath = "/sdcard/Download/mrsohn_capture_temp_${timestamp}.png"
            
            // 1. Capture on device
            val capCmd = mutableListOf(adbPath)
            if (!deviceId.isNullOrBlank()) {
                capCmd.addAll(listOf("-s", deviceId))
            }
            capCmd.addAll(listOf("shell", "screencap", "-p", tempDevicePath))
            
            val capProcess = ProcessBuilder(capCmd).start()
            if (capProcess.waitFor() != 0) return false

            // 2. Pull to PC
            val pullCmd = mutableListOf(adbPath)
            if (!deviceId.isNullOrBlank()) {
                pullCmd.addAll(listOf("-s", deviceId))
            }
            pullCmd.addAll(listOf("pull", tempDevicePath, targetFile.absolutePath))
            
            val pullProcess = ProcessBuilder(pullCmd).start()
            if (pullProcess.waitFor() != 0) return false

            // 3. Remove from device
            val rmCmd = mutableListOf(adbPath)
            if (!deviceId.isNullOrBlank()) {
                rmCmd.addAll(listOf("-s", deviceId))
            }
            rmCmd.addAll(listOf("shell", "rm", tempDevicePath))
            ProcessBuilder(rmCmd).start().waitFor()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Captures the screen and returns the raw bytes of the PNG image.
     * Note: ADB on Windows might mangle binary output due to line ending conversion (LF -> CRLF).
     * This method works best on macOS/Linux. On Windows, 'pulling' a file is safer.
     */
    fun captureScreenToStream(deviceId: String?): ByteArray? {
        return try {
            val cmd = mutableListOf(adbPath)
            if (!deviceId.isNullOrBlank()) {
                cmd.addAll(listOf("-s", deviceId))
            }
            cmd.addAll(listOf("shell", "screencap", "-p"))
            
            val process = ProcessBuilder(cmd).start()
            val bytes = process.inputStream.readAllBytes()
            if (process.waitFor() == 0) {
                bytes
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
