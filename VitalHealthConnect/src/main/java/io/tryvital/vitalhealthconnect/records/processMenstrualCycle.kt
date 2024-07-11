package io.tryvital.vitalhealthconnect.records

import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.SexualActivityRecord
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
    val periodsByPackage = periods.groupBy { it.metadata.dataOrigin.packageName }
    val flowsByPackage = flows.groupBy { it.metadata.dataOrigin.packageName }
    val cervicalMucusByPackage = cervicalMucus.groupBy { it.metadata.dataOrigin.packageName }
    val intermenstrualBleedingByPackage = intermenstrualBleeding.groupBy { it.metadata.dataOrigin.packageName }
    val ovulationTestByPackage = ovulationTest.groupBy { it.metadata.dataOrigin.packageName }
    val sexualActivityByPackage = sexualActivity.groupBy { it.metadata.dataOrigin.packageName }

    val packages = periodsByPackage.keys + flowsByPackage.keys + cervicalMucusByPackage.keys +
            intermenstrualBleedingByPackage.keys + ovulationTestByPackage.keys +
            sexualActivityByPackage.keys

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

    // queryMulti returns sample in `startDate` ascending order.
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
    val intermenstrualBleedingByCycle = groupSamplesByBoundary(currentTimeZone, intermenstrualBleeding, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        MenstrualCycle.IntermenstrualBleedingEntry(date)
    }
    val ovulationTestByCycle = groupSamplesByBoundary(currentTimeZone, ovulationTest, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        val flow = ovulationTestResult(record.result) ?: return@groupSamplesByBoundary null
        MenstrualCycle.OvulationTestEntry(date, flow)
    }
    val sexualActivityByCycle = groupSamplesByBoundary(currentTimeZone, sexualActivity, cycleBoundaries, { it.time to it.zoneOffset }) { date, record ->
        val protectionUsed = protectionUsed(record.protectionUsed)
        MenstrualCycle.SexualActivityEntry(date, protectionUsed)
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
            source = Source(
                type = SourceType.App,
                appId = sourceBundle,
                provider = ProviderSlug.HealthConnect,
            )
        )
    }
}


internal inline fun <reified Record, reified Entry> groupSamplesByBoundary(
    currentTimeZone: ZoneId,
    records: List<Record>,
    boundaries: List<CycleBoundary>,
    recordExtractor: (Record) -> Pair<Instant, ZoneOffset?>,
    transform: (LocalDate, Record) -> Entry?
): Map<CycleBoundary, List<Entry>> {

    // Simple O(N*M) algorithm since the number of entries is low.
    return boundaries.associateWith { boundary ->
        records.mapNotNull { record ->
            val (startTime, zoneOffset) = recordExtractor(record)
            val startDate = startTime.atZone(zoneOffset ?: currentTimeZone).toLocalDate()

            if (!boundary.contains(startDate)) {
                return@mapNotNull null
            }

            transform(startDate, record)
        }
    }
}

internal fun menstrualFlow(rawValue: Int): MenstrualCycle.MenstrualFlow? = when (rawValue) {
    MenstruationFlowRecord.FLOW_HEAVY -> MenstrualCycle.MenstrualFlow.Heavy
    MenstruationFlowRecord.FLOW_MEDIUM -> MenstrualCycle.MenstrualFlow.Medium
    MenstruationFlowRecord.FLOW_LIGHT -> MenstrualCycle.MenstrualFlow.Light
    MenstruationFlowRecord.FLOW_UNKNOWN -> MenstrualCycle.MenstrualFlow.Unspecified
    else -> null
}

internal fun cervicalMucusQuality(rawValue: Int): MenstrualCycle.CervicalMucusQuality? = when (rawValue) {
    CervicalMucusRecord.APPEARANCE_CREAMY -> MenstrualCycle.CervicalMucusQuality.Creamy
    CervicalMucusRecord.APPEARANCE_DRY -> MenstrualCycle.CervicalMucusQuality.Dry
    CervicalMucusRecord.APPEARANCE_STICKY -> MenstrualCycle.CervicalMucusQuality.Sticky
    CervicalMucusRecord.APPEARANCE_EGG_WHITE -> MenstrualCycle.CervicalMucusQuality.EggWhite
    CervicalMucusRecord.APPEARANCE_WATERY -> MenstrualCycle.CervicalMucusQuality.Watery
    else -> null
}

internal fun ovulationTestResult(rawValue: Int): MenstrualCycle.OvulationTestResult? = when (rawValue) {
    OvulationTestRecord.RESULT_INCONCLUSIVE -> MenstrualCycle.OvulationTestResult.Indeterminate
    OvulationTestRecord.RESULT_NEGATIVE -> MenstrualCycle.OvulationTestResult.Negative
    OvulationTestRecord.RESULT_HIGH, OvulationTestRecord.RESULT_POSITIVE -> MenstrualCycle.OvulationTestResult.Positive
    else -> null
}

internal fun protectionUsed(rawValue: Int): Boolean? = when (rawValue) {
    SexualActivityRecord.PROTECTION_USED_PROTECTED -> true
    SexualActivityRecord.PROTECTION_USED_UNPROTECTED -> false
    else -> null
}
