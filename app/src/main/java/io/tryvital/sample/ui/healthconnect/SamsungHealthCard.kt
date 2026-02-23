package io.tryvital.sample.ui.healthconnect

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.ProviderAvailability
import io.tryvital.vitalhealthcore.model.ConnectionStatus
import kotlinx.coroutines.launch

@Composable
fun SamsungHealthCard(
    state: SamsungHealthViewModelState,
    viewModel: SamsungHealthViewModel,
) {
    Card(Modifier.padding(16.dp)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text("Samsung Health", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(12.dp))

            Text("Availability", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            SamsungAvailabilityInfo(state.available)

            Spacer(modifier = Modifier.height(12.dp))

            SamsungConnectionStatusInfo(state.connectionStatus, state.isPerformingConnectionAction, viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            if (state.available == ProviderAvailability.Installed) {
                Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SamsungPermissionInfo(state.permissionsGranted, state.permissionsMissing, viewModel)
            }
        }
    }
}

@Composable
private fun SamsungConnectionStatusInfo(
    connectionStatus: ConnectionStatus,
    isPerformingAction: Boolean,
    viewModel: SamsungHealthViewModel,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Connection Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(connectionStatus.name)
    }

    if (connectionStatus != ConnectionStatus.AutoConnect) {
        Button(
            onClick = { viewModel.toggleConnection() },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            enabled = !isPerformingAction,
        ) {
            if (isPerformingAction) {
                CircularProgressIndicator()
            } else {
                Icon(
                    if (connectionStatus == ConnectionStatus.Disconnected) Icons.Outlined.Login else Icons.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (connectionStatus == ConnectionStatus.Disconnected) "Connect" else "Disconnect")
            }
        }
    }
}

@Composable
private fun SamsungPermissionInfo(
    permissionsGranted: List<VitalResource>,
    permissionsMissing: List<VitalResource>,
    viewModel: SamsungHealthViewModel,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val permissionsLauncher = rememberLauncherForActivityResult(viewModel.createPermissionRequestContract()) { outcomeAsync ->
        coroutineScope.launch {
            val outcome = outcomeAsync.await()
            Toast.makeText(context, outcome.toString(), Toast.LENGTH_LONG).show()
            viewModel.checkPermissions()
        }
    }

    val pauseSync = remember { mutableStateOf(viewModel.pauseSync) }
    val isBackgroundSyncEnabled = remember { mutableStateOf(viewModel.isBackgroundSyncEnabled) }
    val backgroundSyncEnabler = rememberLauncherForActivityResult(viewModel.enableBackgroundSyncContract()) {
        isBackgroundSyncEnabled.value = it
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            if (permissionsMissing.isEmpty()) "All permissions granted" else "Some permissions missing",
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
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Request")
            }

            Button(
                onClick = { viewModel.openSamsungHealth(context) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Open Samsung Health")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = pauseSync.value,
                onCheckedChange = {
                    viewModel.pauseSync = it
                    pauseSync.value = it
                },
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
private fun SamsungAvailabilityInfo(availability: ProviderAvailability?) {
    when (availability) {
        ProviderAvailability.Installed -> {
            Text(
                "This device has Samsung Health installed",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Magenta,
            )
        }

        ProviderAvailability.NotInstalled -> {
            Text(
                "Samsung Health is not installed",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Magenta,
            )
        }

        ProviderAvailability.NotSupportedSDK -> {
            Text(
                "The installed Samsung Health app is incompatible and needs to be upgraded.",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Magenta,
            )
        }

        null -> {
            CircularProgressIndicator()
        }
    }
}
