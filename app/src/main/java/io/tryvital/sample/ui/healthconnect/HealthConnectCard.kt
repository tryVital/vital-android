package io.tryvital.sample.ui.healthconnect

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.HealthConnectConnectionStatus
import io.tryvital.vitalhealthcore.model.VitalResource
import kotlinx.coroutines.launch

@Composable
fun HealthConnectCard(
    state: HealthConnectViewModelState,
    viewModel: HealthConnectViewModel,
) {
    Card(Modifier.padding(16.dp)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text("Health Connect", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(12.dp))

            Text("Availability", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            AvailabilityInfo(state.available)

            Spacer(modifier = Modifier.height(12.dp))

            ConnectionStatusInfo(state.connectionStatus, state.isPerformingConnectionAction, viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            if (state.available == HealthConnectAvailability.Installed) {
                Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                PermissionInfo(state.permissionsGranted, state.permissionsMissing, viewModel)
            }
        }
    }
}

@Composable
fun ConnectionStatusInfo(
    connectionStatus: HealthConnectConnectionStatus,
    isPerformingAction: Boolean,
    viewModel: HealthConnectViewModel
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Connection Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(connectionStatus.name)
    }

    if (connectionStatus != HealthConnectConnectionStatus.AutoConnect) {
        Button(
            onClick = { viewModel.toggleConnection() },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            enabled = !isPerformingAction,
        ) {
            if (isPerformingAction) {
                CircularProgressIndicator()

            } else {
                Icon(
                    if (connectionStatus == HealthConnectConnectionStatus.Disconnected) Icons.Outlined.Login else Icons.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (connectionStatus == HealthConnectConnectionStatus.Disconnected) "Connect" else "Disconnect")
            }
        }
    }
}

@Composable
fun PermissionInfo(
    permissionsGranted: List<VitalResource>,
    permissionsMissing: List<VitalResource>,
    viewModel: HealthConnectViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val permissionsLauncher =
        rememberLauncherForActivityResult(viewModel.createPermissionRequestContract()) { outcomeAsync ->
            coroutineScope.launch {
                val outcome = outcomeAsync.await()
                Toast.makeText(context, outcome.toString(), Toast.LENGTH_LONG).show()
                viewModel.checkPermissions()
            }
        }

    val pauseSync = remember { mutableStateOf(viewModel.pauseSync) }
    val isBackgroundSyncEnabled = remember { mutableStateOf(viewModel.isBackgroundSyncEnabled) }
    val backgroundSyncEnabler =
        rememberLauncherForActivityResult(viewModel.enableBackgroundSyncContract()) {
            isBackgroundSyncEnabled.value = it
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            if (permissionsMissing.isEmpty()) "All permissions granted 👍" else "Some permissions missing 👀",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Magenta,
            textAlign = TextAlign.Start,
        )

        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(0.5F)) {
                Text("Granted", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                permissionsGranted.forEach { permission ->
                    Text(permission.toString(), fontWeight = FontWeight.Normal, fontSize = 11.sp)
                }
                if (permissionsGranted.isEmpty()) {
                    Text("None", fontSize = 11.sp)
                }
            }

            Column(Modifier.weight(0.5F)) {
                Text("Missing", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                permissionsMissing.forEach { permission ->
                    Text(permission.toString(), fontWeight = FontWeight.Normal, fontSize = 11.sp)
                }
                if (permissionsMissing.isEmpty()) {
                    Text("None", fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = { permissionsLauncher.launch(Unit) },
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Icon(
                    Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Request")
            }

            Button(
                onClick = { viewModel.openHealthConnect(context) },
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Open Health Connect")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = pauseSync.value,
                onCheckedChange = {
                    viewModel.pauseSync = it
                    pauseSync.value = it
                }
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Pause Synchronization")
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = isBackgroundSyncEnabled.value,
                onCheckedChange = { checked ->
                    if (checked) {
                        backgroundSyncEnabler.launch(Unit)
                    } else {
                        viewModel.disableBackgroundSync()
                        isBackgroundSyncEnabled.value = false
                    }
                },
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Background Sync (Experimental)")
        }
    }
}

@Composable
private fun AvailabilityInfo(availability: HealthConnectAvailability?) {
    when (availability) {
        HealthConnectAvailability.Installed -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "This device has health connect installed 👍",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Magenta
            )
        }
        HealthConnectAvailability.NotInstalled -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "This device supports health connect", fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Magenta
                )
                Text(
                    "but it is not installed", fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Magenta
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details")
                                    .buildUpon()
                                    .appendQueryParameter(
                                        "id", "com.google.android.apps.healthdata"
                                    )
                                    .appendQueryParameter("url", "healthconnect://onboarding")
                                    .build()
                            )
                        )
                    },
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 12.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Install")
                }

            }
        }

        HealthConnectAvailability.NotSupportedSDK -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "This device is not supported ;(",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Magenta
            )
        }
        null -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}