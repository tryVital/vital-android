package io.tryvital.vitaldevices

import java.io.Serializable

data class ScannedDevice(
    val address: String,
    val name: String,
    val deviceModel: DeviceModel
) : Serializable