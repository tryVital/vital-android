package io.tryvital.vitalsamsunghealth.healthconnect.client.units

class Length private constructor(
    val inMeters: Double,
) {
    companion object {
        @JvmStatic fun meters(value: Double) = Length(value)
        @JvmStatic fun kilometers(value: Double) = Length(value * 1000.0)
    }
}

class Mass private constructor(
    val inKilograms: Double,
) {
    val inGrams: Double get() = inKilograms * 1000.0
    val inMilligrams: Double get() = inKilograms * 1_000_000.0
    val inMicrograms: Double get() = inKilograms * 1_000_000_000.0

    companion object {
        @JvmStatic fun kilograms(value: Double) = Mass(value)
        @JvmStatic fun grams(value: Double) = Mass(value / 1000.0)
        @JvmStatic fun milligrams(value: Double) = Mass(value / 1_000_000.0)
        @JvmStatic fun micrograms(value: Double) = Mass(value / 1_000_000_000.0)
    }
}

class Energy private constructor(
    val inKilocalories: Double,
) {
    companion object {
        @JvmStatic fun kilocalories(value: Double) = Energy(value)
    }
}

class Power private constructor(
    val inKilocaloriesPerDay: Double,
) {
    companion object {
        @JvmStatic fun kilocaloriesPerDay(value: Double) = Power(value)
    }
}

class BloodGlucose private constructor(
    val inMilligramsPerDeciliter: Double,
) {
    companion object {
        @JvmStatic fun milligramsPerDeciliter(value: Double) = BloodGlucose(value)
    }
}

class Volume private constructor(
    val inMilliliters: Double,
) {
    companion object {
        @JvmStatic fun milliliters(value: Double) = Volume(value)
        @JvmStatic fun liters(value: Double) = Volume(value * 1000.0)
    }
}

class Percentage(
    val value: Double,
)

class Temperature private constructor(
    val inCelsius: Double,
) {
    companion object {
        @JvmStatic fun celsius(value: Double) = Temperature(value)
    }
}

class Pressure private constructor(
    val inMillimetersOfMercury: Double,
) {
    companion object {
        @JvmStatic fun millimetersOfMercury(value: Double) = Pressure(value)
    }
}
