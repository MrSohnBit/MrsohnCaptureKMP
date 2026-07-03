package mrsohn.capture.adb

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

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

        // 4. 내장 ZIP 파일에서 압축 해제 시도
        val extractedAdb = extractAdbFromZip()
        if (extractedAdb != null) return extractedAdb

        return adbExecutableName
    }

    private fun extractAdbFromZip(): String? {
        val os = System.getProperty("os.name").lowercase()
        val osSubDir = if (os.contains("win")) "windows" else "macos"
        val zipName = if (os.contains("win")) "platform-tools-win.zip" else "platform-tools-mac.zip"

        // Compose Desktop에서 리소스 디렉토리 찾기
        val resDir = System.getProperty("compose.application.resources.dir")?.let { File(it) }
            ?: File("../sdk/$osSubDir") // 개발 환경용 경로
        
        val zipFile = File(resDir, zipName)
        if (!zipFile.exists()) {
            // 다른 개발 환경용 경로 시도 (프로젝트 루트 기준)
            val devZipFile = File("sdk/$osSubDir", zipName)
            if (!devZipFile.exists()) return null
            else return extractZip(devZipFile)
        }

        return extractZip(zipFile)
    }

    private fun extractZip(zipFile: File): String? {
        val os = System.getProperty("os.name").lowercase()
        val extractDir = getExtractDir()
        if (!extractDir.exists()) extractDir.mkdirs()

        val adbFile = File(extractDir, "platform-tools/$adbExecutableName")
        // 이미 압축 해제되어 있다면 해당 경로 반환
        if (adbFile.exists()) return adbFile.absolutePath

        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val outFile = File(extractDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        // 실행 권한 설정 (Windows 제외)
                        if (!os.contains("win")) {
                            outFile.setExecutable(true)
                        }
                    }
                }
            }
            if (adbFile.exists()) return adbFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getExtractDir(): File {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "MrSohnCapture/adb")
        } else {
            File(System.getProperty("user.home"), "Library/Application Support/MrSohnCapture/adb")
        }
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
