package io.tryvital.vitalhealthcore.model

fun vitalResourceOrder(): List<String> = listOf(
    "profile",
    "body",
    "workout",
    "activity",
    "sleep",
    "glucose",
    "bloodPressure",
    "heartRate",
    "steps",
    "activeEnergyBurned",
    "basalEnergyBurned",
    "floorsClimbed",
    "distanceWalkingRunning",
    "vo2Max",
    "water",
    "heartRateVariability",
    "menstrualCycle",
    "respiratoryRate",
    "temperature",
    "bloodOxygen",
    "meal",
)

fun vitalResourcePriority(name: String): Int = when (name) {
    "activity" -> 10
    "body" -> 11
    "menstrualCycle" -> 12
    "profile" -> 13
    "sleep" -> 20
    "bloodPressure" -> 21
    "glucose" -> 22
    "heartRateVariability" -> 23
    "vo2Max" -> 24
    "water" -> 25
    "respiratoryRate" -> 26
    "temperature" -> 27
    "bloodOxygen" -> 28
    "meal" -> 29
    "workout" -> 31
    "steps" -> 51
    "distanceWalkingRunning" -> 52
    "floorsClimbed" -> 53
    "heartRate" -> 91
    "activeEnergyBurned" -> 92
    "basalEnergyBurned" -> 93
    else -> error("Unknown VitalResource name: $name")
}
