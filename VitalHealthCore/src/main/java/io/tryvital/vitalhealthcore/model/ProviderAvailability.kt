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

    /**
     * Samsung Health only.
     *
     * Samsung Health has been installed. But your App Package ID has not yet been allowlisted
     * by Samsung. Contact Samsumg Developer Support.
     *
     * If you are testing on your device, you can enable Samsung Health developer mode.
     * See https://developer.samsung.com/health/data/guide/developer-mode.html for the instructions.
     */
    AppNotAllowed,
}
