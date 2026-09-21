package io.tryvital.vitalsamsunghealth.records

import android.content.Context
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.device.AccessoryType
import com.samsung.android.sdk.health.data.device.DeviceGroup
import com.samsung.android.sdk.health.data.device.DeviceType
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitalsamsunghealth.SamsungHealthClientProvider
import java.util.concurrent.ConcurrentHashMap

internal fun interface SourceTypeResolver {
    suspend fun resolve(point: HealthDataPoint): SourceType?
}

internal class SamsungSourceTypeResolver(
    context: Context,
    samsungHealthClientProvider: SamsungHealthClientProvider,
) : SourceTypeResolver {
    private val deviceManager by lazy {
        samsungHealthClientProvider.getHealthDataStore(context).getDeviceManager()
    }
    private val cache = ConcurrentHashMap<String, SourceType>()

    override suspend fun resolve(point: HealthDataPoint): SourceType? {
        val dataSource = point.dataSource ?: return null
        val deviceId = dataSource.deviceId
        if (deviceId.isBlank()) return null

        cache[deviceId]?.let { return it }

        val device = runCatching { deviceManager.getDevice(deviceId) }.getOrNull()
            ?: return null
        val sourceType = sourceTypeForDeviceType(device.deviceType)

        sourceType?.let { cache[deviceId] = it }
        return sourceType
    }
}

internal fun sourceTypeForDeviceType(deviceType: DeviceType): SourceType? = when (deviceType) {
    DeviceGroup.MOBILE -> SourceType.Phone
    DeviceGroup.WATCH, DeviceGroup.BAND -> SourceType.Watch
    DeviceGroup.RING -> SourceType.Ring
    AccessoryType.WEIGHT_SCALE -> SourceType.Scale
    AccessoryType.HEART_RATE_MONITOR -> SourceType.ChestStrap
    AccessoryType.BLOOD_PRESSURE_MONITOR -> SourceType.Cuff
    AccessoryType.BLOOD_GLUCOSE_METER -> SourceType.Fingerprick
    else -> null
}
