package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDate

@JsonClass(generateAdapter = true)
data class MenstrualCycle(
    @Json(name = "period_start")
    val periodStart: LocalDate,

    @Json(name = "period_end")
    val periodEnd: LocalDate?,

    @Json(name = "cycle_end")
    val cycleEnd: LocalDate?,

    @Json(name = "menstrual_flow")
    val menstrualFlow: List<MenstrualFlowEntry>,

    @Json(name = "cervical_mucus")
    val cervicalMucus: List<CervicalMucusEntry>,

    @Json(name = "intermenstrual_bleeding")
    val intermenstrualBleeding: List<IntermenstrualBleedingEntry>,

    val contraceptive: List<ContraceptiveEntry>,

    @Json(name = "detected_deviations")
    val detectedDeviations: List<DetectedDeviationEntry>,

    @Json(name = "ovulation_test")
    val ovulationTest: List<OvulationTestEntry>,

    @Json(name = "home_pregnancy_test")
    val homePregnancyTest: List<HomePregnancyTestEntry>,

    @Json(name = "home_progesterone_test")
    val homeProgesteroneTest: List<HomeProgesteroneTestEntry>,

    @Json(name = "sexual_activity")
    val sexualActivity: List<SexualActivityEntry>,

    @Json(name = "basal_body_temperature")
    val basalBodyTemperature: List<BasalBodyTemperatureEntry>,

    val source: Source
) {

    @JsonClass(generateAdapter = false)
    enum class MenstrualFlow {
        @Json(name = "unspecified") Unspecified,
        @Json(name = "none") None,
        @Json(name = "light") Light,
        @Json(name = "medium") Medium,
        @Json(name = "heavy") Heavy;
    }

    @JsonClass(generateAdapter = false)
    enum class MenstrualDeviation {
        @Json(name = "persistent_intermenstrual_bleeding") PersistentIntermenstrualBleeding,
        @Json(name = "prolonged_menstrual_periods") ProlongedMenstrualPeriods,
        @Json(name = "irregular_menstrual_cycles") IrregularMenstrualCycles,
        @Json(name = "infrequent_menstrual_cycles") InfrequentMenstrualCycles;
    }

    @JsonClass(generateAdapter = false)
    enum class CervicalMucusQuality {
        @Json(name = "dry") Dry,
        @Json(name = "sticky") Sticky,
        @Json(name = "creamy") Creamy,
        @Json(name = "watery") Watery,
        @Json(name = "egg_white") EggWhite;
    }

    @JsonClass(generateAdapter = false)
    enum class ContraceptiveType {
        @Json(name = "unspecified") Unspecified,
        @Json(name = "implant") Implant,
        @Json(name = "injection") Injection,
        @Json(name = "iud") IUD,
        @Json(name = "intravaginal_ring") IntravaginalRing,
        @Json(name = "oral") Oral,
        @Json(name = "patch") Patch;
    }

    @JsonClass(generateAdapter = false)
    enum class OvulationTestResult {
        @Json(name = "negative") Negative,
        @Json(name = "positive") Positive,
        @Json(name = "luteinizing_hormone_surge") LuteinizingHormoneSurge,
        @Json(name = "estrogen_surge") EstrogenSurge,
        @Json(name = "indeterminate") Indeterminate;
    }

    @JsonClass(generateAdapter = false)
    enum class HomeTestResult {
        @Json(name = "negative") Negative,
        @Json(name = "positive") Positive,
        @Json(name = "indeterminate") Indeterminate;
    }

    @JsonClass(generateAdapter = true)
    data class MenstrualFlowEntry(
        val date: LocalDate,
        val flow: MenstrualFlow,
    )

    @JsonClass(generateAdapter = true)
    data class CervicalMucusEntry(
        val date: LocalDate,
        val quality: CervicalMucusQuality,
    )

    @JsonClass(generateAdapter = true)
    data class IntermenstrualBleedingEntry(
        val date: LocalDate,
    )

    @JsonClass(generateAdapter = true)
    data class ContraceptiveEntry(
        val date: LocalDate,
        val type: ContraceptiveType,
    )

    @JsonClass(generateAdapter = true)
    data class DetectedDeviationEntry(
        val date: LocalDate,
        val deviation: MenstrualDeviation,
    )

    @JsonClass(generateAdapter = true)
    data class OvulationTestEntry(
        val date: LocalDate,
        @Json(name = "test_result")
        val testResult: OvulationTestResult,
    )

    @JsonClass(generateAdapter = true)
    data class HomePregnancyTestEntry(
        val date: LocalDate,
        @Json(name = "test_result")
        val testResult: HomeTestResult,
    )

    @JsonClass(generateAdapter = true)
    data class HomeProgesteroneTestEntry(
        val date: LocalDate,
        @Json(name = "test_result")
        val testResult: HomeTestResult,
    )

    @JsonClass(generateAdapter = true)
    data class SexualActivityEntry(
        val date: LocalDate,
        @Json(name = "protection_used")
        val protectionUsed: Boolean?,
    )

    @JsonClass(generateAdapter = true)
    data class BasalBodyTemperatureEntry(
        val date: LocalDate,
        val value: Double,
    )
}
