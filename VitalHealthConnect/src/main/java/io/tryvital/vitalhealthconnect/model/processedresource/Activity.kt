package io.tryvital.vitalhealthconnect.model.processedresource

import io.tryvital.client.services.data.ActivityDaySummary
import io.tryvital.client.services.data.ActivityPayload

data class Activity(
    val daySummary: ActivityDaySummary?,
    val activeEnergyBurned: List<QuantitySample>,
    val basalEnergyBurned: List<QuantitySample>,
    val steps: List<QuantitySample>,
    val distanceWalkingRunning: List<QuantitySample>,
    val vo2Max: List<QuantitySample>,
    val floorsClimbed: List<QuantitySample>,
) {
    fun toActivityPayload(): ActivityPayload {
        return ActivityPayload(
            daySummary = daySummary,
            activeEnergyBurned = activeEnergyBurned.map { it.toQuantitySamplePayload() },
            basalEnergyBurned = basalEnergyBurned.map { it.toQuantitySamplePayload() },
            steps = steps.map { it.toQuantitySamplePayload() },
            distanceWalkingRunning = distanceWalkingRunning.map { it.toQuantitySamplePayload() },
            vo2Max = vo2Max.map { it.toQuantitySamplePayload() },
            floorsClimbed = floorsClimbed.map { it.toQuantitySamplePayload() },
        )
    }
}