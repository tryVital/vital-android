package io.tryvital.sample.ui.device

import android.Manifest
import android.content.pm.PackageManager.*
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import io.tryvital.vitaldevices.VitalDeviceManager

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DeviceScreen(vitalDeviceManager: VitalDeviceManager, navController: NavHostController) {
    val deviceId = navController.currentBackStackEntry?.arguments?.getString("deviceId") ?: ""
    val viewModel: DeviceViewModel = viewModel(
        factory = DeviceViewModel.provideFactory(vitalDeviceManager, deviceId)
    )

    val state = viewModel.uiState.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device") },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0)),
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "backIcon")
                    }
                }
            )
        },
        content = { padding ->
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
                    Text(
                        text = "IsConnected " + state.isConnected,
                        style = MaterialTheme.typography.titleSmall
                    )
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
                            val bluetoothScan = Manifest.permission.ACCESS_FINE_LOCATION
                            when (PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    bluetoothScan
                                ) -> {
                                    Log.d("vital", "Got permission")
                                }
                                else -> {
                                    launcher.launch(bluetoothScan)
                                }
                            }
                        }) {
                            Text("Request Permission")
                        }
                        Box(modifier = Modifier.width(16.dp))
                        Button(onClick = {
                            viewModel.scan()
                        }) {
                            Text("Scan")
                        }
                    }

                    Box(modifier = Modifier.height(24.dp))

                    Text(text = "Discovered Devices", style = MaterialTheme.typography.titleMedium)
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
                                modifier = Modifier.clickable(onClick = {
                                    viewModel.connect(
                                        context,
                                        scannedDevice
                                    )
                                }),
                                headlineText = {
                                    Text(text = scannedDevice.deviceModel.name)
                                },
                                supportingText = {
                                    Text(text = scannedDevice.name)
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
                                    Text(text = sample.value + " " + sample.unit)
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
                                        text = "Systolic: " + sample.systolic + " ${sample.systolic.unit} " +
                                                "Diastolic: " + sample.diastolic + " ${sample.diastolic.unit} " +
                                                "Pulse: " + sample.pulse + " ${sample.pulse.unit}"
                                    )
                                },
                                supportingText = {
                                    Text(text = sample.systolic.startDate.toString())
                                },

                                )

                        }
                    }

                }
            )
        }
    )
}