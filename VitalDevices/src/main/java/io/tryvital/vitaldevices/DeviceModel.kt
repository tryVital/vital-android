package io.tryvital.vitaldevices

import java.io.Serializable

data class DeviceModel(
    val name: String,
    val id: String,
    val brand: Brand,
    val kind: Kind
): Serializable