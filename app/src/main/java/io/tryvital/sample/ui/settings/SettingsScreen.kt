package io.tryvital.sample.ui.settings

import android.content.pm.PackageManager.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.sample.Screen
import io.tryvital.vitaldevices.Brand
import io.tryvital.vitaldevices.DeviceModel
import io.tryvital.vitaldevices.Kind

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0)),
            )
        }
    ) { padding ->
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
                "Configured",
                onValueChange = { },
                label = { Text("Status") },
                readOnly = true,
            )

            TextField(
                "user_id",
                onValueChange = { },
                label = { Text("User ID") },
                readOnly = true,
            )

            Text("Configuration")

            TextField(
                "key",
                onValueChange = { },
                label = { Text("API Key") },
            )

            TextField(
                "user_id",
                onValueChange = { },
                label = { Text("User ID") },
            )

            SettingsDropdownMenu(
                title = "Environment",
                items = listOf(
                    Pair(Pair(Environment.Dev, Region.US), "dev - us"),
                    Pair(Pair(Environment.Dev, Region.EU), "dev - eu"),
                ),
                selectedId = Pair(Environment.Dev, Region.US),
                onSelectionChange = { }
            )

            SettingsDropdownMenu(
                title = "Auth Mode",
                items = listOf(
                    Pair("apiKey", "API Key"),
                    Pair("userJwtDemo", "Vital Sign-In Token Demo"),
                ),
                selectedId = Pair(Environment.Dev, Region.US),
                onSelectionChange = { }
            )

            Text("Actions")

            OutlinedButton(onClick = { /*TODO*/ }) {
                Text("Generate User ID")
            }
            OutlinedButton(onClick = { /*TODO*/ }) {
                Text("Configure SDK")
            }
            OutlinedButton(onClick = { /*TODO*/ }) {
                Text("Reset SDK")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsDropdownMenu(
    title: String,
    items: List<Pair<Any, String>>,
    selectedId: Any?,
    onSelectionChange: (Any) -> Unit
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