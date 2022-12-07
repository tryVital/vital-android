package io.tryvital.vitaldevices

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.Context.BLUETOOTH_SERVICE
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.tryvital.client.services.data.QuantitySample
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

    private val vitalLogger = VitalLogger.create()
    private val deviceStateChange = MutableStateFlow<Pair<String, Boolean>?>(null)

    fun search(deviceModel: DeviceModel) = callbackFlow {
        vitalLogger.logI("searching for ${deviceModel.name}")
        if (permissionsGranted()) {
            throw IllegalStateException("Missing permission for BLUETOOTH_SCAN")
        } else if (!bluetoothAdapter.isEnabled) {
            throw IllegalStateException("Bluetooth is not enabled on this device")
        }

        val codes = codes(deviceModel.id)

        val filter = IntentFilter().also {
            it.addAction(BluetoothDevice.ACTION_FOUND)
            it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            it.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            it.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            it.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            it.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        context.registerReceiver(object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        deviceStateChange.value = Pair(device?.address ?: "", true)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        deviceStateChange.value = Pair(device?.address ?: "", false)
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                        if (device?.name == null) {
                            return
                        }

                        vitalLogger.logI("Found device ${device.name}")

                        if (codes.any { device.name.lowercase().contains(it.lowercase()) }) {
                            channel.trySend(
                                ScannedDevice(
                                    address = device.address,
                                    name = device.name,
                                    deviceModel = deviceModel
                                )
                            )
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        vitalLogger.logI("Bond state changed ${device?.bondState}")
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        vitalLogger.logI("Discovery started")

                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        vitalLogger.logI("Discovery finished")

                        channel.close()
                    }
                }
            }
        }, filter)

        val result = bluetoothAdapter.startDiscovery()
        vitalLogger.logI("Discovery result $result")

        awaitClose { }
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

    fun monitorConnection(scannedDevice: ScannedDevice): Flow<Boolean> {
        return deviceStateChange.filterNotNull().filter {
            it.first == scannedDevice.address
        }.map { it.second }
    }

    fun glucoseMeter(context: Context, scannedDevice: ScannedDevice): Flow<List<QuantitySample>> {
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

    @SuppressLint("InlinedApi")
    private fun permissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED
    }


    companion object {
        fun create(context: Context) = VitalDeviceManager(context)
    }
}