package io.tryvital.sample.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.tryvital.sample.AppSettingsStore

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(store: AppSettingsStore, navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(store)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0)),
            )
        }
    ) { padding ->
        val context = LocalContext.current
        val state = viewModel.uiState.collectAsState()
        val focusManager = LocalFocusManager.current

        LaunchedEffect("onLaunch") {
            viewModel.didLaunch(context)
        }

        val error = state.value.currentError
        if (error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text(text = error::class.simpleName ?: "") },
                text = { Text(text = error.message ?: "") },
                confirmButton = {
                    Button(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth(1.0F)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("SDK State")

            TextField(
                if (state.value.appSettings.isSDKConfigured) "Configured" else "null",
                onValueChange = {},
                label = { Text("Status") },
                readOnly = true,
            )

            TextField(
                state.value.sdkUserId,
                onValueChange = {},
                label = { Text("User ID") },
                readOnly = true,
            )

            Text("Configuration")

            TextField(
                state.value.appSettings.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text("API Key") },
                maxLines = 1,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            )

            TextField(
                state.value.appSettings.userId,
                onValueChange = viewModel::setUserId,
                label = { Text("User ID") },
                maxLines = 1,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            )

            SettingsDropdownMenu(
                title = "Environment",
                items = viewModel.environments,
                selectedId = Pair(state.value.appSettings.environment, state.value.appSettings.region),
                onSelectionChange = { (e, r) -> viewModel.setEnvironment(e, r) }
            )

            SettingsDropdownMenu(
                title = "Auth Mode",
                items = viewModel.authModes,
                selectedId = state.value.appSettings.authMode,
                onSelectionChange = viewModel::setAuthMode,
            )

            Text("Actions")

            OutlinedButton(onClick = { viewModel.generateUserID(context) }, enabled = state.value.canGenerateUserId) {
                Text("Generate User ID")
            }
            OutlinedButton(
                onClick = {
                    when (state.value.appSettings.authMode) {
                        SettingsAuthMode.ApiKey -> viewModel.configureSDK(context)
                        SettingsAuthMode.SignInTokenDemo -> viewModel.signInWithToken(context)
                    }
                },
                enabled = state.value.canConfigureSDK
            ) {
                when (state.value.appSettings.authMode) {
                    SettingsAuthMode.ApiKey -> Text("Configure SDK")
                    SettingsAuthMode.SignInTokenDemo -> Text("Sign-In with Token")
                }
            }
            OutlinedButton(onClick = { viewModel.resetSDK(context) }, enabled = state.value.canResetSDK) {
                Text("Reset SDK")
            }

            if (state.value.appSettings.authMode == SettingsAuthMode.SignInTokenDemo) {
                OutlinedButton(
                    onClick = { viewModel.forceTokenRefresh(context) },
                    enabled = state.value.canForceTokenRefresh
                ) {
                    Text("Force Token Refresh")
                }
            }

            Text("Reauth (Migration) Simulation")

            if (state.value.hasSetupReauthObserver) {
                Text("Reauth handler is active...")
            } else {
                OutlinedButton(
                    onClick = { viewModel.simulateReauth(context, simulateSuccess = true) },
                    enabled = state.value.canResetSDK
                ) {
                    Text("Simulate Success")
                }
                OutlinedButton(
                    onClick = { viewModel.simulateReauth(context, simulateSuccess = false) },
                    enabled = state.value.canResetSDK
                ) {
                    Text("Simulate Failure")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun <T> SettingsDropdownMenu(
    title: String,
    items: List<Pair<T, String>>,
    selectedId: T?,
    onSelectionChange: (T) -> Unit
) {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = setExpanded) {
        TextField(
            value = items.first { (id, _) -> id == selectedId }.second,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            label = { Text(title) },
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) }
        ) {
            for ((id, itemTitle) in items) {
                DropdownMenuItem(
                    text = { Text(itemTitle) },
                    onClick = {
                        onSelectionChange(id)
                        setExpanded(false)
                    },
                )
            }
        }
    }
}