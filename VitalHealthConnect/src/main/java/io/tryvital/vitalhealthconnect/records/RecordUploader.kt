@file:OptIn(VitalPrivateApi::class)

package io.tryvital.vitalhealthconnect.records

import io.tryvital.client.VitalClient
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalBody
import io.tryvital.client.services.data.DataStage
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.LocalProfile
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.SummaryPayload
import io.tryvital.client.services.data.TimeseriesPayload
import java.time.Instant


interface RecordUploader {

    suspend fun uploadSleeps(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        sleepPayloads: List<LocalSleep>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadBody(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        bodyPayload: LocalBody,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadProfile(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        profilePayload: LocalProfile,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadActivities(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        activityPayloads: List<LocalActivity>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadWorkouts(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        workoutPayloads: List<LocalWorkout>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadBloodPressure(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        bloodPressurePayloads: List<LocalBloodPressureSample>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        quantitySamples: List<LocalQuantitySample>,
        stage: DataStage = DataStage.Daily,
    )
}

class VitalClientRecordUploader(private val vitalClient: VitalClient) : RecordUploader {
    override suspend fun uploadSleeps(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        sleepPayloads: List<LocalSleep>,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.addSleeps(
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

    override suspend fun uploadBody(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        bodyPayload: LocalBody,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.addBody(
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
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        profilePayload: LocalProfile,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.addProfile(
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
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        activityPayloads: List<LocalActivity>,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.addActivities(
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

    override suspend fun uploadWorkouts(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        workoutPayloads: List<LocalWorkout>,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.addWorkouts(
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

    override suspend fun uploadQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        quantitySamples: List<LocalQuantitySample>,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.timeseriesPost(
            userId, resource.toString(), TimeseriesPayload(
                stage = stage,
                provider = ManualProviderSlug.HealthConnect,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = quantitySamples,
            )
        )
    }

    override suspend fun uploadBloodPressure(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        bloodPressurePayloads: List<LocalBloodPressureSample>,
        stage: DataStage,
    ) {
        vitalClient.vitalPrivateService.bloodPressureTimeseriesPost(
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