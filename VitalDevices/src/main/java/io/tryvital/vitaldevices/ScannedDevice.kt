package io.tryvital.vitaldevices

data class ScannedDevice(
    val address: String,
    val name: String,
    val deviceModel: DeviceModel
) {
    val canPair get() = deviceModel.brand != Brand.Libre
}