package io.tryvital.vitaldevices.devices

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
import no.nordicsemi.android.ble.data.Data
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * GATT Meter base class but for devices not having implemented Record Access Control Point (RACP).
 *
 * These devices do not support the Read All Records operation, and do not post any notification upon
 * end of data transfer. So we can only listen for incoming notifications passively, and ends the streaming
 * based on a fixed timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class GATTMeterWithNoRACP<Sample>(
    context: Context,
    private val serviceID: UUID,
    private val measurementCharacteristicID: UUID,
    private val scannedBluetoothDevice: BluetoothDevice,
    protected val scannedDevice: ScannedDevice,
    private val waitForNextValueTimeout: Duration = 2.seconds,
    private val listenTimeout: Duration = 30.seconds,
) : BleManager(context) {
    private val vitalLogger = VitalLogger.getOrCreate()

    // Keep all incoming measurements in memory until we get to process them.
    // Note that replay buffer is zero sized, so no value is kept in memory in absence of subscribers.
    private val measurements = MutableSharedFlow<Sample>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    private var measurementCharacteristic: BluetoothGattCharacteristic? = null

    private var deviceReady = MutableStateFlow(false)

    abstract fun mapRawData(device: BluetoothDevice, data: Data): Sample?
    abstract fun onReceivedAll(samples: List<Sample>)

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun pair() {
        connect()

        vitalLogger.logI("Explicit pairing by enabling indication on ${scannedDevice.name}")

        // Enabling indications implicitly forces pairing.
        // e.g., Omron does not respond to `ensureBond()` somehow when testing with Android BLE Stack.
        suspendCancellableCoroutine { continuation ->
            enableIndications(measurementCharacteristic)
                .done {
                    vitalLogger.logI("Successfully paired explicitly by enabling indication on ${scannedDevice.name}")
                    continuation.resume(Unit)
                }
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to pair explicitly by enabling indication (error $status)")
                    continuation.resumeWithException(BluetoothError("Failed to explicitly pair (status code = $status)"))
                }
                .enqueue()
        }
    }

    private suspend fun connect(): Unit = suspendCancellableCoroutine { continuation ->
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

                // Unlike GATTMeter, we do not force a bonding (pairing) as part of the connection
                // process.
                //
                // Some of these RACP-less devices do not respond to ensureBond() from the Android
                // BLE Stack, even though they can pair successfully when the pairing is implicitly
                // initiated as part of enabling characteristic indications.
                continuation.resume(Unit)
            }
            .enqueue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun read(): List<Sample> {
        // Connect but without pairing.
        //
        // If we have not paired, it will happen implicitly when we attempt to enable
        // indication on the measurement characteristic.
        //
        // Note that this is a different process from GATTMeter (for devices w/ RACP support)
        connect()

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

        val semaphore = Semaphore(1, 1)

        // (1) Start a new parallel job that listens to measurements and collect them into a list.
        val sampleCollector = launch {
            val samples = mutableListOf<Sample>()

            // `toCollection()` here does not returns normally. Either:
            // (1) `NoMoreSamplesException` is thrown by us upon receipt of a RACP success
            //      response code, and no more samples is expected to be delivered; OR
            // (2) other exceptions are thrown.
            measurements
                .onSubscription { semaphore.release() }
                .transformLatest { record ->
                    // Emit the record immediately.
                    emit(record)
                    vitalLogger.logI("Received one record from ${scannedDevice.name}; waiting for next record...")

                    // Asynchronously start a `this.waitForNextValueTimeout` timeout.
                    //
                    // If a new record is delivered during the delay, this block will be cancelled
                    // by `transformLatest`, before it spawns a new block with the new record.
                    // In other words, the throw below would not be executed unless the timeout
                    // is reached & without any subsequent record delivered.
                    delay(this@GATTMeterWithNoRACP.waitForNextValueTimeout)

                    // If we resume from `delay(_:)` because the timeout is indeed reached,
                    // throw `NoMoreSamplesException` to end the read streaming.
                    vitalLogger.logI("Stopped reading from ${scannedDevice.name} because no subsequent record is delivered before timeout.")
                    throw NoMoreSamplesException
                }
                .onCompletion { e ->
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

        // (2) Start the listen timeout that would cancel sampleCollector.
        launch {
            // Asynchronously start a `this.listenTimeout` timeout, and throw
            // NoMoreSamplesException to end the read streaming if the timeout is
            // reached.
            delay(this@GATTMeterWithNoRACP.listenTimeout)
            vitalLogger.logI("Stopped reading from ${scannedDevice.name} because listen timeout is reached.")
            sampleCollector.cancel(cause = NoMoreSamplesException)
        }

        // (3) We enable notification to signal the RACP-less device to start
        // sending values.
        //
        // If the pairing hasn't been done, this will start the pairing implicitly as well.
        launch {
            // Wait until the sampleCollector job has started properly.
            semaphore.acquire()

            vitalLogger.logI("Enabling measurement indication on ${scannedDevice.name}")

            enableIndications(measurementCharacteristic)
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to enabled measurement indication (error $status)")
                    this@channelFlow.close(BluetoothError(message = "Failed to enabled measurement indication (error $status)"))
                }
                .enqueue()
        }
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return GattCallbackImpl()
    }

    private inner class GattCallbackImpl : BleManagerGattCallback() {

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            vitalLogger.logI("Discovered services: ${gatt.services.map { it.uuid }}")

            val service = gatt.getService(serviceID) ?: return false

            measurementCharacteristic = service.getCharacteristic(measurementCharacteristicID)

            return measurementCharacteristic != null
        }

        override fun initialize() {
            setIndicationCallback(measurementCharacteristic).with { device: BluetoothDevice, data: Data ->
                val sample = mapRawData(device, data) ?: return@with
                val success = measurements.tryEmit(sample)

                if (!success) {
                    vitalLogger.logI("Failed to emit measurement despite unlimited flow buffer size.")
                }
            }

            // Unlike a BLE device with RACP support, we do not want to enable BLE notification here.
            // This is because some RACP-less devices may use the enablement as a signal to
            // start sending records unilaterally, and a lot of them discards the records afterwards.
            // In some cases, they also initiate pairing only when the BLE Central attempts to
            // enable indication on a characteristic.
        }

        override fun onDeviceReady() {
            deviceReady.value = true
        }

        override fun onServicesInvalidated() {
            measurementCharacteristic = null
            deviceReady.value = false
        }
    }
}
