package io.tryvital.vitalsamsunghealth.model

import com.samsung.android.sdk.health.data.data.HealthDataPoint
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SourceType
import java.time.Instant

fun quantitySample(
    value: Double,
    unit: String,
    startDate: Instant,
    endDate: Instant,
    dataPoint: HealthDataPoint? = null,
    sourceType: SourceType? = null,
): LocalQuantitySample {
    return LocalQuantitySample(
        id = dataPoint?.uid,
        value = value,
        unit = unit,
        startDate = startDate,
        endDate = endDate,
        type = sourceType ?: dataPoint?.inferredSourceType,
        sourceBundle = dataPoint?.dataSource?.appId,
        deviceModel = null,
        metadata = emptyMap(),
    )
}

internal val HealthDataPoint.inferredSourceType: SourceType?
    get() = if (dataSource?.deviceId.isNullOrBlank()) SourceType.App else SourceType.Watch
