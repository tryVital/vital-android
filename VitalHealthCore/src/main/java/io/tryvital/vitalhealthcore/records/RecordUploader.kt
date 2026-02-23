package io.tryvital.vitalhealthcore.records

import io.tryvital.client.services.data.DataStage
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalBody
import io.tryvital.client.services.data.LocalMenstrualCycle
import io.tryvital.client.services.data.LocalProfile
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.ManualMealCreation
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

    suspend fun uploadMenstrualCycles(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        cyclePayloads: List<LocalMenstrualCycle>,
        stage: DataStage = DataStage.Daily,
    )

    suspend fun uploadMeals(
        userId: String,
        startDate: Instant?,
        endDate: Instant?,
        timeZoneId: String?,
        mealPayloads: List<ManualMealCreation>,
        stage: DataStage = DataStage.Daily
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
