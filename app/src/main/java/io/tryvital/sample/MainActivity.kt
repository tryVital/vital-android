package io.tryvital.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.tryvital.sample.ui.UsersScreen
import io.tryvital.sample.ui.VitalApp
import io.tryvital.sample.ui.theme.VitalSampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = (application as VitalApp).client
        setContent {
            VitalSampleTheme {
                UsersScreen(client)
            }
        }
    }
}

