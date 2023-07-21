package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.BodyPayload
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.ProfilePayload
import java.util.Date

sealed class ProcessedResourceData {
    data class Summary(val summaryData: SummaryData) : ProcessedResourceData()
    data class TimeSeries(val timeSeriesData: TimeSeriesData) : ProcessedResourceData()

    /**
     * Merge two `ProcessedResourceData`s into one
     * Older data should be on the LHS; Newer on the RHS.
     */
    fun merge(other: ProcessedResourceData): ProcessedResourceData {
        if (this is Summary && other is Summary) {
            return Summary(summaryData.merge(other.summaryData))
        }
        if (this is TimeSeries && other is TimeSeries) {
            return TimeSeries(timeSeriesData.merge(other.timeSeriesData))
        }
        throw IllegalArgumentException("cannot merge two different cases of ProcessedResourceData")
    }
}

sealed class TimeSeriesData {
    abstract fun merge(other: TimeSeriesData): TimeSeriesData

    data class BloodPressure(val samples: List<BloodPressureSample>) : TimeSeriesData() {
        override fun merge(other: TimeSeriesData): TimeSeriesData {
            check(other is BloodPressure)
            return BloodPressure(samples + other.samples)
        }
    }

    data class QuantitySamples(val resource: IngestibleTimeseriesResource, val samples: List<QuantitySample>) : TimeSeriesData() {
        override fun merge(other: TimeSeriesData): TimeSeriesData {
            check(other is QuantitySamples && resource == other.resource)
            return QuantitySamples(resource, samples + other.samples)
        }
    }
}

sealed class SummaryData {
    abstract fun merge(other: SummaryData): SummaryData

    data class Profile(
        val biologicalSex: String,
        val dateOfBirth: Date,
        val heightInCm: Int,
    ) : SummaryData() {

        fun toProfilePayload(): ProfilePayload {
            return ProfilePayload(
                biologicalSex = biologicalSex,
                dateOfBirth = dateOfBirth,
                heightInCm = heightInCm,
            )
        }

        override fun merge(other: SummaryData): SummaryData {
            check(other is Profile)
            // RHS bias
            return other
        }
    }

    data class Body(
        val bodyMass: List<QuantitySample>,
        val bodyFatPercentage: List<QuantitySample>,
    ) : SummaryData() {

        fun toBodyPayload(): BodyPayload {
            return BodyPayload(
                bodyMass = bodyMass.map { it.toQuantitySamplePayload() },
                bodyFatPercentage = bodyFatPercentage.map { it.toQuantitySamplePayload() },
            )
        }

        override fun merge(other: SummaryData): SummaryData {
            check(other is Body)
            // RHS bias
            return other
        }
    }

    data class Activities(
        val activities: List<Activity>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Activities)
            return Activities(activities + other.activities)
        }
    }

    data class Sleeps(
        val samples: List<Sleep>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Sleeps)
            return Sleeps(samples + other.samples)
        }
    }

    data class Workouts(
        val samples: List<Workout>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Workouts)
            return Workouts(samples + other.samples)
        }
    }
}

fun Collection<ProcessedResourceData>.merged(): ProcessedResourceData
    = reduce { acc, next -> acc.merge(next) }
