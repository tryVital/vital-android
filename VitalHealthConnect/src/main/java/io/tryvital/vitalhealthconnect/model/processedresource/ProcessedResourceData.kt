package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.BodyPayload
import io.tryvital.client.services.data.ProfilePayload
import java.util.*

sealed class ProcessedResourceData {
    data class Summary(val summaryData: SummaryData) : ProcessedResourceData()
    data class TimeSeries(val timeSeriesData: TimeSeriesData) : ProcessedResourceData()
}

sealed class TimeSeriesData {
    data class Glucose(val samples: List<QuantitySample>) : TimeSeriesData()
    data class BloodPressure(val samples: List<BloodPressureSample>) : TimeSeriesData()
    data class HeartRate(val samples: List<QuantitySample>) : TimeSeriesData()
    data class HeartRateVariabilityRmssd(val samples: List<QuantitySample>) : TimeSeriesData()
    data class Water(val samples: List<QuantitySample>) : TimeSeriesData()
}

sealed class SummaryData {
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
    }

    data class Activities(
        val activities: List<Activity>
    ) : SummaryData()

    data class Sleeps(
        val samples: List<Sleep>
    ) : SummaryData()

    data class Workouts(
        val samples: List<Workout>
    ) : SummaryData()
}

