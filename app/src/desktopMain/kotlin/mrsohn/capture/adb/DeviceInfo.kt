package mrsohn.capture.adb

data class DeviceInfo(
    val id: String,
    val model: String,
    val status: String = "device"
) {
    override fun toString(): String = "$model ($id)"
}
