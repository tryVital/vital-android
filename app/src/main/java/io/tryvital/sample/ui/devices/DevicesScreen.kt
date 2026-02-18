package io.tryvital.sample.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import io.tryvital.sample.Screen
import io.tryvital.vitaldevices.DeviceModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DevicesScreen(navController: NavHostController) {

    val viewModel: DevicesViewModel = viewModel(
        factory = DevicesViewModel.provideFactory()
    )

    val state = viewModel.uiState.collectAsState().value

    Scaffold(topBar = {
        TopAppBar(title = { Text("Supported devices") },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0)),
        )
    }, content = { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()), content = {
                Box(modifier = Modifier.height(32.dp))
                Text(
                    text = "Glucose devices",
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    )
                )
                state.glucoseDevices.forEach { device ->
                    DeviceItem(navController, device)
                }
                Box(modifier = Modifier.height(32.dp))
                Text(
                    text = "Blood pressure devices",
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    )
                )
                Column {
                    state.bloodPressureDevices.forEach { device ->
                        DeviceItem(navController, device)

                    }
                }
                Box(modifier = Modifier.height(32.dp))
            })
    })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DeviceItem(
    navController: NavHostController,
    device: DeviceModel
) {
    ListItem(
        modifier = Modifier.clickable(
            null,
            onClick = {
                navController.navigate(Screen.Device.route + device.id)
            },
            indication = LocalIndication.current,
        ),
        headlineText = {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
            )
        },
        supportingText = {
            Text(
                text = device.brand.name,
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingContent = {
            Image(
                painter = rememberAsyncImagePainter(deviceImageUrl(device)),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        })
}

fun deviceImageUrl(device: DeviceModel): String {
    return when (device.id) {
        "omron_m4" -> "https://storage.googleapis.com/vital-assets/omron_m4.jpeg"
        "omron_m7" -> "https://storage.googleapis.com/vital-assets/omron_m7.jpeg"
        "accuchek_guide" -> "https://storage.googleapis.com/vital-assets/accu_check_guide.png"
        "accuchek_guide_active" -> "https://storage.googleapis.com/vital-assets/accu_check_active.png"
        "accuchek_guide_me" -> "https://storage.googleapis.com/vital-assets/accu_chek_guide_me.jpeg"
        "contour_next_one" -> "https://storage.googleapis.com/vital-assets/Contour.png"
        "beurer" -> "https://storage.googleapis.com/vital-assets/beurer.png"
        else -> "http://placekitten.com/200/200"
    }
}
