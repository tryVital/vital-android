package io.tryvital.vitalhealthconnect.records

import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import java.util.*

private const val stage = "daily"

interface RecordUploader {

    suspend fun uploadSleeps(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        sleepPayloads: List<SleepPayload>,
    )


    suspend fun uploadBody(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bodyPayload: BodyPayload
    )

    suspend fun uploadProfile(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        profilePayload: ProfilePayload
    )

    suspend fun uploadActivities(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        activityPayloads: List<ActivityPayload>,
    )

    suspend fun uploadWorkouts(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        workoutPayloads: List<WorkoutPayload>,
    )

    suspend fun uploadGlucose(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        glucosePayloads: List<QuantitySamplePayload>
    )

    suspend fun uploadBloodPressure(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        bloodPressurePayloads: List<BloodPressureSamplePayload>
    )

    suspend fun uploadHeartRate(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        heartRatePayloads: List<QuantitySamplePayload>
    )

    suspend fun uploadHeartRateVariability(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        heartRatePayloads: List<QuantitySamplePayload>
    )

    suspend fun uploadWater(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        waterPayloads: List<QuantitySamplePayload>
    )
}

class VitalClientRecordUploader(private val vitalClient: VitalClient) : RecordUploader {
    override suspend fun uploadSleeps(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        sleepPayloads: List<SleepPayload>,
    ) {
        if (sleepPayloads.isNotEmpty()) {
            vitalClient.summaryService.addSleeps(
                userId, SummaryPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = sleepPayloads,
                )
            )
        }
    }

    override suspend fun uploadBody(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bodyPayload: BodyPayload
    ) {
        vitalClient.summaryService.addBody(
            userId, SummaryPayload(
                stage = stage,
                provider = ManualProviderSlug.HealthConnect,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = bodyPayload
            )
        )
    }

    override suspend fun uploadProfile(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        profilePayload: ProfilePayload
    ) {
        vitalClient.summaryService.addProfile(
            userId, SummaryPayload(
                stage = stage,
                provider = ManualProviderSlug.HealthConnect,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = profilePayload
            )
        )
    }

    override suspend fun uploadActivities(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        activityPayloads: List<ActivityPayload>,
    ) {
        if (activityPayloads.isNotEmpty()) {
            vitalClient.summaryService.addActivities(
                userId, SummaryPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = activityPayloads,
                )
            )
        }
    }

    override suspend fun uploadWorkouts(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        workoutPayloads: List<WorkoutPayload>,
    ) {
        if (workoutPayloads.isNotEmpty()) {
            vitalClient.summaryService.addWorkouts(
                userId, SummaryPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = workoutPayloads,
                )
            )
        }
    }

    override suspend fun uploadGlucose(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        glucosePayloads: List<QuantitySamplePayload>
    ) {
        if (glucosePayloads.isNotEmpty()) {
            vitalClient.vitalsService.sendGlucose(
                userId, TimeseriesPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = glucosePayloads,
                )
            )
        }
    }

    override suspend fun uploadBloodPressure(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        bloodPressurePayloads: List<BloodPressureSamplePayload>
    ) {
        if (bloodPressurePayloads.isNotEmpty()) {
            vitalClient.vitalsService.sendBloodPressure(
                userId, TimeseriesPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = bloodPressurePayloads,
                )
            )
        }
    }

    override suspend fun uploadHeartRate(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        heartRatePayloads: List<QuantitySamplePayload>
    ) {
        if (heartRatePayloads.isNotEmpty()) {
            vitalClient.vitalsService.sendHeartRate(
                userId, TimeseriesPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = heartRatePayloads,
                )
            )
        }
    }

    override suspend fun uploadHeartRateVariability(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        heartRatePayloads: List<QuantitySamplePayload>
    ) {
        if (heartRatePayloads.isNotEmpty()) {
            vitalClient.vitalsService.sendHeartRateVariability(
                userId, TimeseriesPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = heartRatePayloads,
                )
            )
        }
    }

    override suspend fun uploadWater(
        userId: String,
        startDate: Date,
        endDate: Date,
        timeZoneId: String?,
        waterPayloads: List<QuantitySamplePayload>
    ) {
        if (waterPayloads.isNotEmpty()) {
            vitalClient.vitalsService.sendWater(
                userId, TimeseriesPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = waterPayloads,
                )
            )
        }
    }
}