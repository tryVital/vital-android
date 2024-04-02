package io.tryvital.vitalhealthconnect.records

import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.ActivityPayload
import io.tryvital.client.services.data.BloodPressureSamplePayload
import io.tryvital.client.services.data.BodyPayload
import io.tryvital.client.services.data.DataStage
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.ProfilePayload
import io.tryvital.client.services.data.QuantitySamplePayload
import io.tryvital.client.services.data.SleepPayload
import io.tryvital.client.services.data.SummaryPayload
import io.tryvital.client.services.data.TimeseriesPayload
import io.tryvital.client.services.data.WorkoutPayload
import java.util.Date


interface RecordUploader {

    suspend fun uploadSleeps(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        sleepPayloads: List<SleepPayload>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadBody(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bodyPayload: BodyPayload,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadProfile(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        profilePayload: ProfilePayload,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadActivities(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        activityPayloads: List<ActivityPayload>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadWorkouts(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        workoutPayloads: List<WorkoutPayload>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadBloodPressure(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bloodPressurePayloads: List<BloodPressureSamplePayload>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        quantitySamples: List<QuantitySamplePayload>,
        stage: DataStage = DataStage.Daily,
    )
}

class VitalClientRecordUploader(private val vitalClient: VitalClient) : RecordUploader {
    override suspend fun uploadSleeps(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        sleepPayloads: List<SleepPayload>,
        stage: DataStage,
    ) {
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

    override suspend fun uploadBody(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bodyPayload: BodyPayload,
        stage: DataStage,
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
        profilePayload: ProfilePayload,
        stage: DataStage,
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
        stage: DataStage,
    ) {
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

    override suspend fun uploadWorkouts(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        workoutPayloads: List<WorkoutPayload>,
        stage: DataStage,
    ) {
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

    override suspend fun uploadQuantitySamples(
        resource: IngestibleTimeseriesResource,
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        quantitySamples: List<QuantitySamplePayload>,
        stage: DataStage,
    ) {
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

    override suspend fun uploadBloodPressure(
        userId: String,
        startDate: Date?,
        endDate: Date?,
        timeZoneId: String?,
        bloodPressurePayloads: List<BloodPressureSamplePayload>,
        stage: DataStage,
    ) {
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