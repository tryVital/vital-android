package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.ActivityDaySummary
import io.tryvital.client.services.data.LocalActivity
import io.tryvital.client.services.data.LocalQuantitySample

data class Activity(
    val daySummary: ActivityDaySummary?,
    val activeEnergyBurned: List<LocalQuantitySample>,
    val basalEnergyBurned: List<LocalQuantitySample>,
    val steps: List<LocalQuantitySample>,
    val distanceWalkingRunning: List<LocalQuantitySample>,
    val vo2Max: List<LocalQuantitySample>,
    val floorsClimbed: List<LocalQuantitySample>,
) {
    fun toActivityPayload(): LocalActivity {
        return LocalActivity(
            daySummary = daySummary,
            activeEnergyBurned = activeEnergyBurned,
            basalEnergyBurned = basalEnergyBurned,
            steps = steps,
            distanceWalkingRunning = distanceWalkingRunning,
            vo2Max = vo2Max,
            floorsClimbed = floorsClimbed,
        )
    }
}
