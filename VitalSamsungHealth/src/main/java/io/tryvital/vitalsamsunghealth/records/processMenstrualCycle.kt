package io.tryvital.vitalsamsunghealth.records

import io.tryvital.client.services.data.LocalMenstrualCycle
import io.tryvital.client.services.data.MenstrualCycle
import io.tryvital.client.services.data.ProviderSlug
import io.tryvital.client.services.data.Source
import io.tryvital.client.services.data.SourceType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

internal data class MenstruationPeriodRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset?,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset?,
    val sourceBundle: String,
)

internal data class MenstruationFlowRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val flow: Int,
    val sourceBundle: String,
)

internal data class CervicalMucusRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val appearance: Int,
    val sourceBundle: String,
)

internal data class IntermenstrualBleedingRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val sourceBundle: String,
)

internal data class OvulationTestRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val result: Int,
    val sourceBundle: String,
)

internal data class SexualActivityRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val protectionUsed: Int,
    val sourceBundle: String,
)

private object MenstrualConsts {
    const val FLOW_UNKNOWN = 0
    const val FLOW_LIGHT = 1
    const val FLOW_MEDIUM = 2
    const val FLOW_HEAVY = 3

    const val CERVICAL_CREAMY = 1
    const val CERVICAL_DRY = 2
    const val CERVICAL_STICKY = 3
    const val CERVICAL_EGG_WHITE = 4
    const val CERVICAL_WATERY = 5

    const val OVULATION_INCONCLUSIVE = 0
    const val OVULATION_NEGATIVE = 1
    const val OVULATION_POSITIVE = 2
    const val OVULATION_HIGH = 3

    const val PROTECTION_UNKNOWN = 0
    const val PROTECTION_USED = 1
    const val PROTECTION_NOT_USED = 2
}

internal data class CycleBoundary(
    val cycleStart: LocalDate,
    val periodEnd: LocalDate?,
    val cycleEnd: LocalDate?,
) {
    fun contains(date: LocalDate): Boolean {
        if (cycleEnd != null) {
            return cycleStart <= date && date <= cycleEnd
        }

        return cycleStart <= date
    }
}

internal fun processMenstrualCycle(
    periods: List<MenstruationPeriodRecord>,
    flows: List<MenstruationFlowRecord>,
    cervicalMucus: List<CervicalMucusRecord>,
    intermenstrualBleeding: List<IntermenstrualBleedingRecord>,
    ovulationTest: List<OvulationTestRecord>,
    sexualActivity: List<SexualActivityRecord>,
): List<LocalMenstrualCycle> {
    val periodsByPackage = periods.groupBy { it.sourceBundle }
    val flowsByPackage = flows.groupBy { it.sourceBundle }
    val cervicalMucusByPackage = cervicalMucus.groupBy { it.sourceBundle }
    val intermenstrualBleedingByPackage = intermenstrualBleeding.groupBy { it.sourceBundle }
    val ovulationTestByPackage = ovulationTest.groupBy { it.sourceBundle }
    val sexualActivityByPackage = sexualActivity.groupBy { it.sourceBundle }

    val packages = periodsByPackage.keys + flowsByPackage.keys + cervicalMucusByPackage.keys +
        intermenstrualBleedingByPackage.keys + ovulationTestByPackage.keys + sexualActivityByPackage.keys

    return packages.flatMap { packageName ->
        val cycles = _processMenstrualCycle(
            packageName,
            periodsByPackage[packageName] ?: emptyList(),
            flowsByPackage[packageName] ?: emptyList(),
            cervicalMucusByPackage[packageName] ?: emptyList(),
            intermenstrualBleedingByPackage[packageName] ?: emptyList(),
            ovulationTestByPackage[packageName] ?: emptyList(),
            sexualActivityByPackage[packageName] ?: emptyList(),
        )

        cycles.map { LocalMenstrualCycle(packageName, it) }
    }
}

internal fun _processMenstrualCycle(
    sourceBundle: String,
    periods: List<MenstruationPeriodRecord>,
    flows: List<MenstruationFlowRecord>,
    cervicalMucus: List<CervicalMucusRecord>,
    intermenstrualBleeding: List<IntermenstrualBleedingRecord>,
    ovulationTest: List<OvulationTestRecord>,
    sexualActivity: List<SexualActivityRecord>,
): List<MenstrualCycle> {

    val cycleBoundaries = mutableListOf<CycleBoundary>()

    var currentCycleStart: LocalDate? = null
    var currentPeriodEnd: LocalDate? = null

    fun endCurrentCycle(newStart: LocalDate?) {
        val cycleStart = currentCycleStart ?: return

        cycleBoundaries.add(
            CycleBoundary(
                cycleStart = cycleStart,
                periodEnd = currentPeriodEnd,
                cycleEnd = newStart?.minusDays(1),
            )
        )

        currentCycleStart = null
        currentPeriodEnd = null
    }

    val currentTimeZone = TimeZone.getDefault().toZoneId()

    for (period in periods) {
        val startDate = period.startTime.atZone(period.startZoneOffset ?: currentTimeZone).toLocalDate()
        val endDate = period.endTime.atZone(period.endZoneOffset ?: currentTimeZone).toLocalDate()

        endCurrentCycle(startDate)
        currentCycleStart = startDate
        currentPeriodEnd = endDate
    }

    endCurrentCycle(newStart = null)

    val flowsByCycle = groupSamplesByBoundary(currentTimeZone, flows, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        val flow = menstrualFlow(record.flow) ?: return@groupSamplesByBoundary null
        MenstrualCycle.MenstrualFlowEntry(date, flow)
    }
    val cervicalMucusByCycle = groupSamplesByBoundary(currentTimeZone, cervicalMucus, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        val quality = cervicalMucusQuality(record.appearance) ?: return@groupSamplesByBoundary null
        MenstrualCycle.CervicalMucusEntry(date, quality)
    }
    val intermenstrualBleedingByCycle = groupSamplesByBoundary(currentTimeZone, intermenstrualBleeding, cycleBoundaries, { it.time to it.zoneOffset }) { date, _ ->
        MenstrualCycle.IntermenstrualBleedingEntry(date)
    }
    val ovulationTestByCycle = groupSamplesByBoundary(currentTimeZone, ovulationTest, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        val result = ovulationTestResult(record.result) ?: return@groupSamplesByBoundary null
        MenstrualCycle.OvulationTestEntry(date, result)
    }
    val sexualActivityByCycle = groupSamplesByBoundary(currentTimeZone, sexualActivity, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        MenstrualCycle.SexualActivityEntry(date, protectionUsed(record.protectionUsed))
    }

    return cycleBoundaries.map { boundary ->
        MenstrualCycle(
            periodStart = boundary.cycleStart,
            periodEnd = boundary.periodEnd,
            cycleEnd = boundary.cycleEnd,
            menstrualFlow = flowsByCycle[boundary] ?: emptyList(),
            cervicalMucus = cervicalMucusByCycle[boundary] ?: emptyList(),
            intermenstrualBleeding = intermenstrualBleedingByCycle[boundary] ?: emptyList(),
            ovulationTest = ovulationTestByCycle[boundary] ?: emptyList(),
            sexualActivity = sexualActivityByCycle[boundary] ?: emptyList(),
            detectedDeviations = emptyList(),
            basalBodyTemperature = emptyList(),
            contraceptive = emptyList(),
            homePregnancyTest = emptyList(),
            homeProgesteroneTest = emptyList(),
            source = Source(type = SourceType.App, appId = sourceBundle, provider = ProviderSlug.SamsungHealth),
        )
    }
}

internal inline fun <reified Record, reified Entry> groupSamplesByBoundary(
    currentTimeZone: ZoneId,
    records: List<Record>,
    boundaries: List<CycleBoundary>,
    recordExtractor: (Record) -> Pair<Instant, ZoneOffset?>,
    transform: (LocalDate, Record) -> Entry?,
): Map<CycleBoundary, List<Entry>> {
    return boundaries.associateWith { boundary ->
        records.mapNotNull { record ->
            val (startTime, zoneOffset) = recordExtractor(record)
            val startDate = startTime.atZone(zoneOffset ?: currentTimeZone).toLocalDate()

            if (!boundary.contains(startDate)) return@mapNotNull null

            transform(startDate, record)
        }
    }
}

internal fun menstrualFlow(rawValue: Int): MenstrualCycle.MenstrualFlow? = when (rawValue) {
    MenstrualConsts.FLOW_HEAVY -> MenstrualCycle.MenstrualFlow.Heavy
    MenstrualConsts.FLOW_MEDIUM -> MenstrualCycle.MenstrualFlow.Medium
    MenstrualConsts.FLOW_LIGHT -> MenstrualCycle.MenstrualFlow.Light
    MenstrualConsts.FLOW_UNKNOWN -> MenstrualCycle.MenstrualFlow.Unspecified
    else -> null
}

internal fun cervicalMucusQuality(rawValue: Int): MenstrualCycle.CervicalMucusQuality? = when (rawValue) {
    MenstrualConsts.CERVICAL_CREAMY -> MenstrualCycle.CervicalMucusQuality.Creamy
    MenstrualConsts.CERVICAL_DRY -> MenstrualCycle.CervicalMucusQuality.Dry
    MenstrualConsts.CERVICAL_STICKY -> MenstrualCycle.CervicalMucusQuality.Sticky
    MenstrualConsts.CERVICAL_EGG_WHITE -> MenstrualCycle.CervicalMucusQuality.EggWhite
    MenstrualConsts.CERVICAL_WATERY -> MenstrualCycle.CervicalMucusQuality.Watery
    else -> null
}

internal fun ovulationTestResult(rawValue: Int): MenstrualCycle.OvulationTestResult? = when (rawValue) {
    MenstrualConsts.OVULATION_INCONCLUSIVE -> MenstrualCycle.OvulationTestResult.Indeterminate
    MenstrualConsts.OVULATION_NEGATIVE -> MenstrualCycle.OvulationTestResult.Negative
    MenstrualConsts.OVULATION_HIGH,
    MenstrualConsts.OVULATION_POSITIVE -> MenstrualCycle.OvulationTestResult.Positive
    else -> null
}

internal fun protectionUsed(rawValue: Int): Boolean? = when (rawValue) {
    MenstrualConsts.PROTECTION_USED -> true
    MenstrualConsts.PROTECTION_NOT_USED -> false
    MenstrualConsts.PROTECTION_UNKNOWN -> null
    else -> null
}
