package io.tryvital.vitalhealthcore.model

enum class ProviderAvailability {
    /**
     * Health Connect or Samsung Health.
     *
     * There is an active Samsung Health or Health Connect installation on this device.
     */
    Installed,

    /**
     * Health Connect or Samsung Health.
     *
     * No Samsung Health or Health Connect installation has been detected on this device.
     */
    NotInstalled,

    /**
     * Health Connect or Samsung Health.
     *
     * The SDK being used is incompatible with the Samsung Health or Health Connect installation.
     */
    NotSupportedSDK,

    /**
     * Samsung Health only.
     *
     * Samsung Health has been installed, but the onboarding experience is incomplete.
     * So all the SDK methods are blocked.
     */
    OnboardingIncomplete,
}
