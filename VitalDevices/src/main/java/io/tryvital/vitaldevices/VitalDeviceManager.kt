package io.tryvital.vitaldevices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.Context.BLUETOOTH_SERVICE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.devices.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class PermissionMissing(permission: String): Throwable("Missing $permission permission")

@Suppress("OPT_IN_USAGE")
@SuppressLint("MissingPermission")
class VitalDeviceManager(
    private val context: Context
) {
    private val bluetoothManager by lazy { context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }

    private val vitalLogger = VitalLogger.getOrCreate()

    fun connected(deviceModel: DeviceModel): List<ScannedDevice> {
        checkPermissions(scan = false, connect = true)
        return bluetoothAdapter.bondedDevices.mapNotNull { mapIfSuitable(it, deviceModel) }
    }

    fun search(deviceModel: DeviceModel) = callbackFlow {
        vitalLogger.logI("Searching for ${deviceModel.name}")

        checkPermissions(scan = true, connect = false)

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

    fun glucoseMeter(context: Context, scannedDevice: ScannedDevice): GlucoseMeter {
        checkPermissions(scan = false, connect = true)

        when (scannedDevice.deviceModel.brand) {
            Brand.AccuChek,
            Brand.Contour -> {
                return GlucoseMeter1808(
                    context,
                    bluetoothAdapter.getRemoteDevice(scannedDevice.address),
                    scannedDevice,
                )
            }
            else -> throw IllegalStateException("${scannedDevice.deviceModel.brand} is not supported")
        }
    }

    fun bloodPressure(
        context: Context,
        scannedDevice: ScannedDevice
    ): BloodPressureReader {
        checkPermissions(scan = false, connect = true)

        when (scannedDevice.deviceModel.brand) {
            Brand.Omron,
            Brand.Beurer -> {
                return BloodPressureReader1810(
                    context,
                    bluetoothAdapter.getRemoteDevice(scannedDevice.address),
                    scannedDevice,
                )
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

    private fun checkPermissions(scan: Boolean, connect: Boolean) {
        if (!bluetoothAdapter.isEnabled) {
            throw IllegalStateException("Bluetooth is not enabled on this device")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (connect && context.checkCallingOrSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PERMISSION_GRANTED) {
                throw PermissionMissing(android.Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (scan && context.checkCallingOrSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PERMISSION_GRANTED) {
                throw PermissionMissing(android.Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (context.checkCallingOrSelfPermission(android.Manifest.permission.BLUETOOTH) != PERMISSION_GRANTED) {
                throw PermissionMissing(android.Manifest.permission.BLUETOOTH)
            }
        }
    }

    companion object {
        fun create(context: Context) = VitalDeviceManager(context)
    }
}