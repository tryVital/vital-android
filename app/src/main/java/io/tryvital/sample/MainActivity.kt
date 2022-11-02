package io.tryvital.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.tryvital.sample.ui.healthconnect.HealthConnectScreen
import io.tryvital.sample.ui.theme.VitalSampleTheme
import io.tryvital.sample.ui.users.UsersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vitalApp = application as VitalApp
        val client = vitalApp.client
        val userRepository = vitalApp.userRepository
        val vitalHealthConnectManager = vitalApp.vitalHealthConnectManager

        setContent {
            val navController = rememberNavController()

            VitalSampleTheme {
                NavHost(navController = navController, startDestination = "users") {
                    composable(Screen.Users.route) {
                        UsersScreen(
                            client,
                            navController,
                            userRepository
                        )
                    }
                    composable(Screen.HealthConnect.route) {
                        HealthConnectScreen(
                            vitalHealthConnectManager,
                            userRepository
                        )
                    }
                    /*...*/
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Users : Screen("users")
    object HealthConnect : Screen("healthConnect")
}