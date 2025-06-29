package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.HealthConnectRecordCollection
import io.tryvital.client.services.data.LocalBody
import io.tryvital.client.services.data.IngestibleTimeseriesResource
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalMenstrualCycle
import io.tryvital.client.services.data.LocalProfile
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.LocalSleep
import io.tryvital.client.services.data.LocalWorkout
import io.tryvital.client.services.data.ManualMealCreation
import io.tryvital.client.services.data.MenstrualCycle
import java.time.Instant

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

    fun isNotEmpty() = when (this) {
        is Summary -> this.summaryData.isNotEmpty()
        is TimeSeries -> this.timeSeriesData.isNotEmpty()
    }

    val count: Int get() = when (this) {
        is Summary -> summaryData.count
        is TimeSeries -> timeSeriesData.count
    }
}

sealed class TimeSeriesData {
    abstract fun merge(other: TimeSeriesData): TimeSeriesData
    abstract fun isNotEmpty(): Boolean
    abstract val count: Int

    data class BloodPressure(val samples: List<LocalBloodPressureSample>) : TimeSeriesData() {
        override fun merge(other: TimeSeriesData): TimeSeriesData {
            check(other is BloodPressure)
            return BloodPressure(samples + other.samples)
        }
        override fun isNotEmpty(): Boolean = samples.isNotEmpty()
        override val count: Int get() = samples.size
    }

    data class QuantitySamples(val resource: IngestibleTimeseriesResource, val samples: List<LocalQuantitySample>) : TimeSeriesData() {
        override fun merge(other: TimeSeriesData): TimeSeriesData {
            check(other is QuantitySamples && resource == other.resource)
            return QuantitySamples(resource, samples + other.samples)
        }
        override fun isNotEmpty(): Boolean = samples.isNotEmpty()
        override val count: Int get() = samples.size
    }
}

sealed class SummaryData {
    abstract fun merge(other: SummaryData): SummaryData
    abstract fun isNotEmpty(): Boolean
    abstract val count: Int

    data class Profile(
        val biologicalSex: String?,
        val dateOfBirth: Instant?,
        val heightInCm: Int?,
    ) : SummaryData() {

        fun toProfilePayload(): LocalProfile {
            return LocalProfile(
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

        override fun isNotEmpty(): Boolean = true
        override val count: Int get() = 1
    }

    data class Body(
        val bodyMass: List<LocalQuantitySample>,
        val bodyFatPercentage: List<LocalQuantitySample>,
    ) : SummaryData() {

        fun toBodyPayload(): LocalBody {
            return LocalBody(
                bodyMass = bodyMass,
                bodyFatPercentage = bodyFatPercentage,
            )
        }

        override fun merge(other: SummaryData): SummaryData {
            check(other is Body)
            // RHS bias
            return other
        }

        override fun isNotEmpty(): Boolean = bodyMass.isNotEmpty() || bodyFatPercentage.isNotEmpty()
        override val count: Int get() = bodyMass.size + bodyFatPercentage.size
    }

    data class Activities(
        val activities: List<LocalActivity>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Activities)
            return Activities(activities + other.activities)
        }
        override fun isNotEmpty(): Boolean = activities.isNotEmpty()
        override val count: Int get() = activities.size
    }

    data class Sleeps(
        val samples: List<LocalSleep>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Sleeps)
            return Sleeps(samples + other.samples)
        }
        override fun isNotEmpty(): Boolean = samples.isNotEmpty()
        override val count: Int get() = samples.size
    }

    data class Workouts(
        val samples: List<LocalWorkout>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Workouts)
            return Workouts(samples + other.samples)
        }
        override fun isNotEmpty(): Boolean = samples.isNotEmpty()
        override val count: Int get() = samples.size
    }

    data class MenstrualCycles(
        val cycles: List<LocalMenstrualCycle>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is MenstrualCycles)
            return MenstrualCycles(cycles + other.cycles)
        }
        override fun isNotEmpty(): Boolean = cycles.isNotEmpty()
        override val count: Int get() = cycles.size
    }

    data class Meals(
        val meals: List<ManualMealCreation>
    ) : SummaryData() {
        override fun merge(other: SummaryData): SummaryData {
            check(other is Meals)
            return Meals(meals + other.meals )
        }
        override fun isNotEmpty(): Boolean = meals.isNotEmpty()
        override val count: Int get() = meals.size
    }
}

fun Collection<ProcessedResourceData>.merged(): ProcessedResourceData
    = reduce { acc, next -> acc.merge(next) }
