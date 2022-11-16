package io.tryvital.vitaldevices

data class DeviceModel(
    val name: String,
    val id: String,
    val brand: Brand,
    val kind: Kind
)