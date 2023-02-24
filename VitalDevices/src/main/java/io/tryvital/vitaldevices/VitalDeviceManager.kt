package io.tryvital.vitaldevices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.Context.BLUETOOTH_SERVICE
import io.tryvital.client.services.data.QuantitySamplePayload
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.devices.BloodPressureReader1810
import io.tryvital.vitaldevices.devices.BloodPressureSample
import io.tryvital.vitaldevices.devices.GlucoseMeter1808
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

@Suppress("OPT_IN_USAGE")
@SuppressLint("MissingPermission")
class VitalDeviceManager(
    private val context: Context
) {
    private val bluetoothManager by lazy { context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }

    private var glucoseMeter1808: GlucoseMeter1808? = null
    private var bloodPressureReader1810: BloodPressureReader1810? = null

    private val vitalLogger = VitalLogger.getOrCreate()

    fun connected(deviceModel: DeviceModel): List<ScannedDevice> {
        return bluetoothAdapter.bondedDevices.mapNotNull { mapIfSuitable(it, deviceModel) }
    }
    fun search(deviceModel: DeviceModel) = callbackFlow {
        vitalLogger.logI("Searching for ${deviceModel.name}")

        if (!bluetoothAdapter.isEnabled) {
            throw IllegalStateException("Bluetooth is not enabled on this device")
        }

        val expectedServiceUUID = arrayOf(serviceUUID(deviceModel.kind))
        vitalLogger.logI("Scanning for BLE service $expectedServiceUUID")

        fun onDeviceDiscovered(device: BluetoothDevice) {
            vitalLogger.logI("Discovered ${deviceModel.kind} ${device.name} uuid=${device.name}")
            val scannedDevice = mapIfSuitable(device, deviceModel)
            if (scannedDevice != null) {
                trySend(scannedDevice)
            }
        }

        val callback = LeScanCallback { device, _, _ ->
            onDeviceDiscovered(device)
        }

        bluetoothAdapter.startLeScan(expectedServiceUUID, callback)

        for (device in bluetoothAdapter.bondedDevices) {
            onDeviceDiscovered(device)
        }

        awaitClose {
            bluetoothAdapter.stopLeScan(callback)
            vitalLogger.logI("Closing search")
        }
    }

    fun pair(scannedDevice: ScannedDevice): Flow<Boolean> {
        return when (scannedDevice.deviceModel.kind) {
            Kind.GlucoseMeter -> GlucoseMeter1808(
                context,
                bluetoothAdapter.getRemoteDevice(scannedDevice.address),
                scannedDevice,
            ).pair()
            Kind.BloodPressure -> BloodPressureReader1810(
                context,
                bluetoothAdapter.getRemoteDevice(scannedDevice.address),
                scannedDevice,
            ).pair()
        }
    }

    fun glucoseMeter(context: Context, scannedDevice: ScannedDevice): Flow<List<QuantitySamplePayload>> {
        when (scannedDevice.deviceModel.brand) {
            Brand.AccuChek,
            Brand.Contour -> {
                glucoseMeter1808 = GlucoseMeter1808(
                    context,
                    bluetoothAdapter.getRemoteDevice(scannedDevice.address),
                    scannedDevice,
                )
                return glucoseMeter1808!!.pair().filter { it }
                    .flatMapConcat { glucoseMeter1808!!.read() }
            }
            else -> throw IllegalStateException("${scannedDevice.deviceModel.brand} is not supported")
        }
    }

    fun bloodPressure(
        context: Context,
        scannedDevice: ScannedDevice
    ): Flow<List<BloodPressureSample>> {
        when (scannedDevice.deviceModel.brand) {
            Brand.Omron,
            Brand.Beurer -> {
                bloodPressureReader1810 = BloodPressureReader1810(
                    context,
                    bluetoothAdapter.getRemoteDevice(scannedDevice.address),
                    scannedDevice,
                )

                return bloodPressureReader1810!!.pair().filter { it }
                    .flatMapConcat { bloodPressureReader1810!!.read() }
            }
            else -> throw IllegalStateException("${scannedDevice.deviceModel.brand} is not supported")
        }
    }

    private fun mapIfSuitable(device: BluetoothDevice, deviceModel: DeviceModel): ScannedDevice? {
        val codes = codes(deviceModel.id)

        if (codes.any { device.name.lowercase().contains(it.lowercase()) } || codes.contains(VITAL_BLE_SIMULATOR)) {
            return ScannedDevice(
                address = device.address,
                name = device.name,
                deviceModel = deviceModel
            )
        }

        return null
    }

    companion object {
        fun create(context: Context) = VitalDeviceManager(context)
    }
}