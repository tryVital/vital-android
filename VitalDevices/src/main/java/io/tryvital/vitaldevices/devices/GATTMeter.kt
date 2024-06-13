package io.tryvital.vitaldevices.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.BluetoothError
import io.tryvital.vitaldevices.NoMoreSamplesException
import io.tryvital.vitaldevices.ScannedDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.common.callback.RecordAccessControlPointResponse
import no.nordicsemi.android.ble.common.data.RecordAccessControlPointData
import no.nordicsemi.android.ble.data.Data
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Standard Record Access Control Point characteristic as per BLE GATT
private val racpCharacteristicUUID =
    UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")

abstract class GATTMeter<Sample>(
    context: Context,
    private val serviceID: UUID,
    private val measurementCharacteristicID: UUID,
    private val scannedBluetoothDevice: BluetoothDevice,
    protected val scannedDevice: ScannedDevice,
) : BleManager(context) {
    private val vitalLogger = VitalLogger.getOrCreate()

    // Keep all incoming measurements in memory until we get to process them.
    // Note that replay buffer is zero sized, so no value is kept in memory in absence of subscribers.
    private val measurements = MutableSharedFlow<Sample>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    // Keep all incoming response in memory until we get to process them.
    // Note that replay buffer is zero sized, so no value is kept in memory in absence of subscribers.
    private val racpResponse = MutableSharedFlow<RecordAccessControlPointResponse>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    private var measurementCharacteristic: BluetoothGattCharacteristic? = null
    private var racpCharacteristic: BluetoothGattCharacteristic? = null

    private var deviceReady = MutableStateFlow(false)

    abstract fun mapRawData(device: BluetoothDevice, data: Data): Sample?
    abstract fun onReceivedAll(samples: List<Sample>)

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun pair(): Unit = suspendCancellableCoroutine { continuation ->
        // The device is already connected. Don't bother to connect again.
        if (isConnected) {
            return@suspendCancellableCoroutine
        }

        connect(scannedBluetoothDevice)
            .retry(3, 100)
            .timeout(15000)
            .useAutoConnect(false)
            .fail { _, status ->
                vitalLogger.logI("Failed to connect (status code = $status)")

                if (!continuation.isActive) {
                    vitalLogger.logI("Inactive continuation has received connect fail callback for ${scannedDevice.name}")
                    return@fail
                }
                continuation.resumeWithException(BluetoothError("Failed to connect (status code = $status)"))
            }
            .done {
                vitalLogger.logI("Successfully connected to ${scannedDevice.name}; bonded = $isBonded")

                if (!continuation.isActive) {
                    vitalLogger.logI("Inactive continuation has received connect done callback for ${scannedDevice.name}")
                    return@done
                }

                if (isBonded) {
                    continuation.resume(Unit)
                } else {
                    vitalLogger.logI("Start bonding with ${scannedDevice.name}")

                    bond(
                        onDone = { continuation.resume(Unit) },
                        onFail = { status ->
                            continuation.resumeWithException(BluetoothError("Failed to bond (status code = $status"))
                        }
                    )
                }
            }
            .enqueue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun read(): List<Sample> {
        pair()

        withTimeout(10 * 1000) {
            // Wait for deviceReady to be `true`, or timeout the flow after 10 seconds.
            deviceReady.filter { it }.first()
        }

        val samples = readSamples().single()

        // Disconnect when we have done reading. Some devices rely on BLE disconnection as
        // a cue to toast users with a "Transferred Completed" message.
        disconnect().enqueue()

        return samples
    }

    private fun readSamples(): Flow<List<Sample>> = channelFlow {
        vitalLogger.logI("Start reading from ${scannedDevice.name}")

        val semaphore = Semaphore(2, 2)

        // (1) Start a new parallel job that listens to measurements and collect them into a list.
        val sampleCollector = launch {
            val samples = mutableListOf<Sample>()

            // `toCollection()` here does not returns normally. Either:
            // (1) `NoMoreSamplesException` is thrown by us upon receipt of a RACP success
            //      response code, and no more samples is expected to be delivered; OR
            // (2) other exceptions are thrown.
            measurements
                .onSubscription { semaphore.release() }
                .onCompletion {e ->
                    if (e is NoMoreSamplesException || e?.cause is NoMoreSamplesException) {
                        assert(!this@channelFlow.isClosedForSend)
                        vitalLogger.logI("Emitting ${samples.count()} samples from ${scannedDevice.name}.")

                        val sampleList = samples.toList()
                        onReceivedAll(sampleList)

                        // Send out the samples, and close the channel normally.
                        this@channelFlow.send(sampleList)
                        close()
                    } else {
                        vitalLogger.logE("Unexpected sample collector completion from ${scannedDevice.name}.", e)
                    }
                }
                .toCollection(samples)
        }

        // (2) Start a parallel job that listens to RACP response indication.
        launch {
            val response = racpResponse
                .onSubscription { semaphore.release() }
                .first()

            if (response.isOperationCompleted) {
                // Cancel the sample collector with `NoMoreSamplesException`, so that it stops
                // collecting samples & sends out what it has.
                sampleCollector.cancel(cause = NoMoreSamplesException)
            } else {
                // RACP operation has failed. Throw to error the whole flow.
                throw BluetoothError("Received RACP failure response with code ${response.errorCode}")
            }
        }

        // (3) Wait until both jobs have started properly.
        semaphore.acquire()
        semaphore.acquire()

        // (4) Write to the RACP to initiate the Load All Records operation.
        writeCharacteristic(
            racpCharacteristic,
            RecordAccessControlPointData.reportAllStoredRecords(),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
            .fail { _, status ->
                vitalLogger.logI("Failed to write characteristic (status code = $status)")
                this.close(BluetoothError("Failed to write to the RACP characteristic."))
            }
            .done { vitalLogger.logI("Successfully initiated the read operation via an RACP write.") }
            .enqueue()
    }

    @SuppressLint("MissingPermission")
    private fun bond(onDone: () -> Unit, onFail: (Int) -> Unit) {
        ensureBond()
            .fail { _, status ->
                vitalLogger.logI("Failed to bond (status code = $status)")
                onFail(status)
            }
            .done {
                vitalLogger.logI("Bonded with ${scannedDevice.name}")
                onDone()
            }
            .enqueue()
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return GattCallbackImpl()
    }

    private inner class GattCallbackImpl : BleManagerGattCallback() {

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            vitalLogger.logI("Discovered services: ${gatt.services.map { it.uuid }}")

            val service = gatt.getService(serviceID) ?: return false

            measurementCharacteristic = service.getCharacteristic(measurementCharacteristicID)
            racpCharacteristic = service.getCharacteristic(racpCharacteristicUUID)

            return measurementCharacteristic != null && racpCharacteristic != null
        }

        override fun initialize() {
            setNotificationCallback(measurementCharacteristic).with { device: BluetoothDevice, data: Data ->
                val sample = mapRawData(device, data) ?: return@with
                val success = measurements.tryEmit(sample)

                if (!success) {
                    vitalLogger.logI("Failed to emit measurement despite unlimited flow buffer size.")
                }
            }
            setIndicationCallback(racpCharacteristic).with { device: BluetoothDevice, data: Data ->
                val response = RecordAccessControlPointResponse().apply {
                    onDataReceived(device, data)
                }
                val success = racpResponse.tryEmit(response)

                if (!success) {
                    vitalLogger.logI("Failed to emit RACP response despite unlimited flow buffer size.")
                }
            }

            enableNotifications(measurementCharacteristic)
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to enabled measurement notifications (error $status)")
                }
                .enqueue()
            enableIndications(racpCharacteristic)
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to enabled RACP indications (error $status)")
                }
                .enqueue()
        }

        override fun onDeviceReady() {
            deviceReady.value = true
        }

        override fun onServicesInvalidated() {
            racpCharacteristic = null
            measurementCharacteristic = null
            deviceReady.value = false
        }
    }
}