package mrsohn.capture.adb

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdbRunner(private val customAdbPath: String? = null) {

    private val adbExecutableName: String by lazy {
        if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
    }

    val adbPath: String by lazy {
        if (!customAdbPath.isNullOrBlank()) {
            val file = File(customAdbPath)
            val adbFile = if (file.isDirectory) {
                File(file, adbExecutableName)
            } else {
                file
            }
            if (adbFile.exists()) {
                return@lazy adbFile.absolutePath
            }
        }
        findAdbPath()
    }

    private fun findAdbPath(): String {
        // 1. 기본 OS별 경로 확인
        val home = System.getProperty("user.home")
        val macDefaultAdb = File(home, "Library/Android/sdk/platform-tools/$adbExecutableName")
        if (macDefaultAdb.exists()) return macDefaultAdb.absolutePath

        val winDefaultAdb = File(System.getenv("LOCALAPPDATA") ?: "", "Android/Sdk/platform-tools/adb.exe")
        if (winDefaultAdb.exists()) return winDefaultAdb.absolutePath

        // 2. 환경 변수 확인
        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (sdkRoot != null) {
            val adb = File(sdkRoot, "platform-tools/$adbExecutableName")
            if (adb.exists()) return adb.absolutePath
        }

        // 3. 현재 실행 폴더 내 platform-tools 확인
        val localAdb = File("platform-tools/$adbExecutableName")
        if (localAdb.exists()) return localAdb.absolutePath

        return adbExecutableName
    }

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
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val tempDevicePath = "/sdcard/Download/mrsohn_capture_temp_${timestamp}.png"
            
            val capCmd = mutableListOf(adbPath)
            if (!deviceId.isNullOrBlank()) {
                capCmd.addAll(listOf("-s", deviceId))
            }
            capCmd.addAll(listOf("shell", "screencap", "-p", tempDevicePath))
            
            val capProcess = ProcessBuilder(capCmd).start()
            if (capProcess.waitFor() != 0) return false

            val pullCmd = mutableListOf(adbPath)
            if (!deviceId.isNullOrBlank()) {
                pullCmd.addAll(listOf("-s", deviceId))
            }
            pullCmd.addAll(listOf("pull", tempDevicePath, targetFile.absolutePath))
            
            val pullProcess = ProcessBuilder(pullCmd).start()
            if (pullProcess.waitFor() != 0) return false

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

    fun connectWireless(deviceId: String): String? {
        return try {
            // 1. IP 주소 가져오기
            val ipCmd = listOf(adbPath, "-s", deviceId, "shell", "ip", "addr", "show", "wlan0")
            val ipProcess = ProcessBuilder(ipCmd).start()
            val reader = BufferedReader(InputStreamReader(ipProcess.inputStream))
            var ip: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.contains("inet ")) {
                    ip = currentLine.trim().substringAfter("inet ").substringBefore("/")
                    break
                }
            }
            ipProcess.waitFor()

            if (ip == null) return null

            // 2. TCPIP 모드 활성화 (5555 포트)
            val tcpipCmd = listOf(adbPath, "-s", deviceId, "tcpip", "5555")
            ProcessBuilder(tcpipCmd).start().waitFor()

            // 3. 연결
            val connectCmd = listOf(adbPath, "connect", "$ip:5555")
            val connectProcess = ProcessBuilder(connectCmd).start()
            val connectReader = BufferedReader(InputStreamReader(connectProcess.inputStream))
            val result = connectReader.readText()
            connectProcess.waitFor()

            if (result.contains("connected to")) "$ip:5555" else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun disconnect(deviceId: String): Boolean {
        return try {
            val process = ProcessBuilder(adbPath, "disconnect", deviceId).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
