package io.tryvital.vitalhealthconnect.model

enum class HealthConnectConnectionStatus {
    /**
     * The Health SDK is using [ConnectionPolicy.AutoConnect].
     */
    AutoConnect,

    /**
     * There is an active Health Connect connection.
     * The Health SDK is using [ConnectionPolicy.Explicit].
     */
    Connected,

    /**
     * There is no active Health Connect connection.
     * The Health SDK is using [ConnectionPolicy.Explicit].
     */
    Disconnected,

    /**
     * There is an active Health Connect connection, but it is paused due to user ingestion bounds set via the Junction API.
     * The Health SDK is using [ConnectionPolicy.Explicit].
     */
    ConnectionPaused;
}