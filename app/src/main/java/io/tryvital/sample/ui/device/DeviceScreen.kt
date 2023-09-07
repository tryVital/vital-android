package io.tryvital.sample.ui.device

import android.Manifest
import android.content.pm.PackageManager.*
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import io.tryvital.sample.ui.devices.deviceImageUrl
import io.tryvital.vitaldevices.Brand
import io.tryvital.vitaldevices.VitalDeviceManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DeviceScreen(vitalDeviceManager: VitalDeviceManager, navController: NavHostController) {
    val deviceId = navController.currentBackStackEntry?.arguments?.getString("deviceId") ?: ""
    val viewModel: DeviceViewModel = viewModel(
        factory = DeviceViewModel.provideFactory(vitalDeviceManager, deviceId)
    )

    val context = LocalContext.current
    val state = viewModel.uiState.collectAsState().value

    LaunchedEffect(key1 = Unit) {
        viewModel.toasts
            .onEach { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            .launchIn(this)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Device") },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0)),
            navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(Icons.Filled.ArrowBack, "backIcon")
                }
            })
    }, content = { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = {

                Image(
                    painter = rememberAsyncImagePainter(deviceImageUrl(state.device)),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp)
                )
                Text(text = state.device.name, style = MaterialTheme.typography.titleLarge)
                Box(modifier = Modifier.height(24.dp))

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        Log.d("vital", "PERMISSION GRANTED")

                    } else {
                        Log.d("vital", "PERMISSION DENIED")
                    }
                }

                val context = LocalContext.current

                Row {
                    Button(onClick = {
                        if (state.device.brand == Brand.Libre) {
                            val nfcPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.NFC
                            )

                            if (nfcPermission != PERMISSION_GRANTED) {
                                launcher.launch(Manifest.permission.NFC)
                            }

                            return@Button
                        }

                        // Needed on older devices
                        val fineLocationPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        )

                        if (fineLocationPermission != PERMISSION_GRANTED) {
                            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }


                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                            val bluetoothPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.BLUETOOTH
                            )
                            if (bluetoothPermission != PERMISSION_GRANTED) {
                                launcher.launch(Manifest.permission.BLUETOOTH)
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val bluetoothScanPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.BLUETOOTH_SCAN
                            )

                            if (bluetoothScanPermission != PERMISSION_GRANTED) {
                                launcher.launch(Manifest.permission.BLUETOOTH_SCAN)
                            }

                            val bluetoothConnectPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.BLUETOOTH_CONNECT
                            )

                            if (bluetoothConnectPermission != PERMISSION_GRANTED) {
                                launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                    }) {
                        Text("Request Permission")
                    }

                    if (state.canScan) {
                        Box(modifier = Modifier.width(16.dp))
                        Button(onClick = {
                            if (state.isScanning) {
                                viewModel.stopScanning()
                            } else {
                                viewModel.scan()
                            }
                        }) {
                            if (state.isScanning) {
                                Text("Stop Scanning")
                            } else {
                                Text("Scan")
                            }
                        }
                    }
                }

                Box(modifier = Modifier.height(24.dp))

                Text(
                    text = "Discovered Devices",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.scannedDevices.isEmpty()) {
                    Box(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No devices found yet",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Box(modifier = Modifier.height(12.dp))
                }
                Column {
                    state.scannedDevices.forEach { scannedDevice ->
                        ListItem(
                            headlineText = {
                                Text(text = scannedDevice.deviceModel.name)
                            },
                            supportingText = {
                                Text(text = scannedDevice.name)
                            },
                            trailingContent = {
                                Row {
                                    if (scannedDevice.canPair) {
                                        Button(onClick = {
                                            viewModel.pair(context, scannedDevice)
                                        }) {
                                            Text("Pair")
                                        }
                                        Box(modifier = Modifier.width(4.dp))
                                    }
                                    Button(onClick = {
                                        viewModel.connect(context, context as ComponentActivity, scannedDevice)
                                    }) {
                                        Text("Read")
                                    }
                                }
                            },
                        )
                    }
                }

                Text(text = "Reading from device", style = MaterialTheme.typography.titleMedium)
                if (state.samples.isEmpty()) {
                    Box(modifier = Modifier.height(12.dp))
                    Text(text = "No samples yet", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.height(12.dp))
                }
                Column {
                    state.samples.forEach { sample ->
                        ListItem(
                            headlineText = {
                                Text(text = "${sample.value} ${sample.unit}")
                            },
                            supportingText = {
                                Text(text = sample.startDate.toString())
                            },

                            )

                    }
                }
                Column {
                    state.bloodPressureSamples.forEach { sample ->
                        ListItem(
                            headlineText = {
                                Text(
                                    text = "Systolic: " + sample.systolic + " ${sample.systolic.unit} " + "Diastolic: " + sample.diastolic + " ${sample.diastolic.unit} " + "Pulse: " + (sample.pulse?.let { "${it.value} ${it.unit}" } ?: "null")
                                )
                            },
                            supportingText = {
                                Text(text = sample.systolic.startDate.toString())
                            },
                        )
                    }
                }
            })
    })
}