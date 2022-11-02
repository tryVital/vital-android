package io.tryvital.sample.ui.healthconnect

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import io.tryvital.vitalhealthconnect.HealthConnectAvailability

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
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                Text("Availability", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            AvailabilityInfo(state.available)
            Spacer(modifier = Modifier.height(12.dp))
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            PermissionInfo(state.permissionsGranted, viewModel)
        }
    }
}

@Composable
fun PermissionInfo(permissionsGranted: Boolean?, viewModel: HealthConnectViewModel) {
    when (permissionsGranted) {
        true -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "All permission granted 👍",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Magenta
            )
        }
        false -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val permissionsLauncher =
                rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
                    viewModel.checkPermissions(context)
                }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Not all permission granted", fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Magenta
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        permissionsLauncher.launch(viewModel.getPermissions())
                    },
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 12.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Request")
                }

            }
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