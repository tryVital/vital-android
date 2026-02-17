package io.tryvital.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.tryvital.client.utils.VitalLogger
import io.tryvital.sample.ui.device.DeviceScreen
import io.tryvital.sample.ui.devices.DevicesScreen
import io.tryvital.sample.ui.healthconnect.HealthConnectScreen
import io.tryvital.sample.ui.healthconnect.SamsungHealthScreen
import io.tryvital.sample.ui.settings.SettingsScreen
import io.tryvital.sample.ui.theme.VitalSampleTheme
import io.tryvital.sample.ui.users.UsersScreen

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vitalApp = application as VitalApp
        val vitalDeviceManager = vitalApp.vitalDeviceManager
        val userRepository = vitalApp.userRepository
        val settingsStore = AppSettingsStore.getOrCreate(this.applicationContext)
        VitalLogger.getOrCreate().enabled = true

        setContent {
            val navController = rememberNavController()
            val backStackEntry = navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry.value?.destination?.route

            @Composable
            fun RowScope.makeBarItem(label: String, vector: ImageVector, screen: Screen) {
                NavigationBarItem(
                    label = { Text(label) },
                    icon = {
                        Icon(
                            imageVector = vector,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = { navController.navigate(screen.route) },
                    selected = currentRoute == screen.route,
                )
            }

            VitalSampleTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            makeBarItem("Users", Icons.Default.List, Screen.Users)
                            makeBarItem("Devices", Icons.Default.Bluetooth, Screen.Devices)
                            makeBarItem("Settings", Icons.Default.Settings, Screen.Settings)
                        }
                    }
                ) { padding ->
                    NavHost(
                        modifier = Modifier.padding(padding),
                        navController = navController,
                        startDestination = Screen.Users.route,
                    ) {
                        composable(Screen.Users.route) {
                            UsersScreen(navController, settingsStore, userRepository)
                        }
                        composable(Screen.HealthConnect.route) {
                            HealthConnectScreen(navController)
                        }
                        composable(Screen.SamsungHealth.route) {
                            SamsungHealthScreen(navController)
                        }
                        composable(Screen.Device.route + "{deviceId}") {
                            DeviceScreen(vitalDeviceManager, navController)
                        }
                        composable(Screen.Devices.route) {
                            DevicesScreen(navController)
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(settingsStore, navController)
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Users : Screen("users")
    object HealthConnect : Screen("healthConnect")
    object SamsungHealth : Screen("samsungHealth")
    object Devices : Screen("devices")
    object Device : Screen("device")
    object Settings : Screen("settings")
}
