package io.tryvital.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.tryvital.client.utils.VitalLogger
import io.tryvital.sample.ui.device.DeviceScreen
import io.tryvital.sample.ui.devices.DevicesScreen
import io.tryvital.sample.ui.healthconnect.HealthConnectScreen
import io.tryvital.sample.ui.theme.VitalSampleTheme
import io.tryvital.sample.ui.users.UsersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vitalApp = application as VitalApp
        val client = vitalApp.client
        val vitalDeviceManager = vitalApp.vitalDeviceManager
        val vitalHealthConnectManager = vitalApp.vitalHealthConnectManager
        val userRepository = vitalApp.userRepository
        VitalLogger.getOrCreate().enabled = true

        setContent {
            val navController = rememberNavController()

            VitalSampleTheme {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Users.route
                ) {
                    composable(Screen.Users.route) {
                        UsersScreen(
                            client,
                            navController,
                            userRepository
                        )
                    }
                    composable(Screen.HealthConnect.route) {
                        HealthConnectScreen(
                            client,
                            vitalHealthConnectManager,
                            userRepository,
                            navController,
                        )
                    }
                    composable(Screen.Device.route + "{deviceId}") {
                        DeviceScreen(
                            vitalDeviceManager,
                            navController,
                        )
                    }
                    composable(Screen.Devices.route) {
                        DevicesScreen(
                            navController,
                        )
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Users : Screen("users")
    object HealthConnect : Screen("healthConnect")
    object Devices : Screen("devices")
    object Device : Screen("device")
}