package io.tryvital.vitalhealthconnect.workers

import io.tryvital.client.services.data.DataStage
import io.tryvital.vitalhealthconnect.model.processedresource.ProcessedResourceData
import io.tryvital.vitalhealthconnect.model.processedresource.SummaryData
import io.tryvital.vitalhealthconnect.model.processedresource.TimeSeriesData
import io.tryvital.vitalhealthconnect.records.RecordUploader
import java.time.Instant
import java.util.Date

internal suspend fun uploadResources(
    data: ProcessedResourceData,
    uploader: RecordUploader,
    stage: DataStage,
    userId: String,
    start: Instant? = null,
    end: Instant? = null,
    timeZoneId: String
) {
    when (data) {
        is ProcessedResourceData.Summary -> when (data.summaryData) {
            is SummaryData.Activities -> uploader.uploadActivities(
                userId, start, end, timeZoneId,
                data.summaryData.activities.map { it.toActivityPayload() },
                stage,
            )
            is SummaryData.Body -> uploader.uploadBody(
                userId, start, end, timeZoneId,
                data.summaryData.toBodyPayload(),
                stage,
            )
            is SummaryData.Profile -> uploader.uploadProfile(
                userId, start, end, timeZoneId,
                data.summaryData.toProfilePayload(),
                stage,
            )
            is SummaryData.Sleeps -> uploader.uploadSleeps(
                userId, start, end, timeZoneId,
                data.summaryData.samples.map { it.toSleepPayload() },
                stage,
            )
            is SummaryData.Workouts -> uploader.uploadWorkouts(
                userId, start, end, timeZoneId,
                data.summaryData.samples.map { it.toWorkoutPayload() },
                stage,
            )
        }

        is ProcessedResourceData.TimeSeries -> when (data.timeSeriesData) {
            is TimeSeriesData.BloodPressure -> uploader.uploadBloodPressure(
                userId, start, end, timeZoneId,
                data.timeSeriesData.samples.map { it.toBloodPressurePayload() },
                stage,
            )
            is TimeSeriesData.QuantitySamples -> uploader.uploadQuantitySamples(
                data.timeSeriesData.resource,
                userId, start, end, timeZoneId,
                data.timeSeriesData.samples.map { it.toQuantitySamplePayload() },
                stage,
            )
        }
    }
}