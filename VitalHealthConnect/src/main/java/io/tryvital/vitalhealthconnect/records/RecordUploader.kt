package io.tryvital.vitalhealthconnect.records

import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.*
import java.util.*

private val stage = DataStage.Daily

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

    suspend fun uploadBloodPressure(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bloodPressurePayloads: List<BloodPressureSamplePayload>
    )

    suspend fun uploadQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        quantitySamples: List<QuantitySamplePayload>
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

    override suspend fun uploadQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        quantitySamples: List<QuantitySamplePayload>
    ) {
        if (quantitySamples.isNotEmpty()) {
            vitalClient.vitalsService.sendQuantitySamples(
                resource, userId, TimeseriesPayload(
                    stage = stage,
                    provider = ManualProviderSlug.HealthConnect,
                    startDate = startDate,
                    endDate = endDate,
                    timeZoneId = timeZoneId,
                    data = quantitySamples,
                )
            )
        }
    }

    override suspend fun uploadBloodPressure(
        userId: String,
        startDate: Date?,
        endDate: Date?,
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
}