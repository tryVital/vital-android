package io.tryvital.sample.ui.healthconnect

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tryvital.client.VitalClient
import io.tryvital.sample.UserRepository

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HealthConnectScreen(client: VitalClient, userRepository: UserRepository) {

    val viewModel: HealthConnectViewModel = viewModel(
        factory = HealthConnectViewModel.provideFactory(client, userRepository)
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.init(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAvailability(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    val state = viewModel.uiState.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect") },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0))
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding), content = {
            UserDetailsCard(state)
            HealthConnectCard(state, viewModel)
        })
    }
}
