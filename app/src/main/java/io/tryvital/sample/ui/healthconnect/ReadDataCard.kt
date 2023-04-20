package io.tryvital.sample.ui.healthconnect

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tryvital.vitalhealthconnect.model.HealthConnectAvailability
import io.tryvital.vitalhealthconnect.model.VitalResource

@Composable
fun ReadDataCard(
    state: HealthConnectViewModelState,
    viewModel: HealthConnectViewModel,
) {
    if (state.available != HealthConnectAvailability.Installed) {
        return
    } else {
        Card(Modifier.padding(16.dp)) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Read data", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(text = state.syncStatus)
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(
                        onClick = {
                            viewModel.linkProvider()
                        },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            top = 12.dp,
                            end = 20.dp,
                            bottom = 12.dp
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Healing,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Link Provider")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            viewModel.sync()
                        },
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            top = 12.dp,
                            end = 20.dp,
                            bottom = 12.dp
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Healing,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Sync")
                    }
                }
                Button(
                    onClick = {
                        viewModel.addGlucose()
                    },
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 12.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.Healing,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Add Glucose")
                }
                Button(
                    onClick = {
                        viewModel.addWater()
                    },
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 12.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    )
                ) {
                    Icon(
                        Icons.Outlined.Healing,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Add Water")
                }
            }
            Box(modifier = Modifier.height(16.dp))
            Text("Print data to console")
            VitalResource.values().forEach {
                ResourceReaderButton(viewModel, it)
            }
            Box(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResourceReaderButton(
    viewModel: HealthConnectViewModel,
    resource: VitalResource
) {
    Button(
        onClick = {
            viewModel.readResource(resource)
        },
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = 12.dp
        )
    ) {
        Text("Read ${resource.name}")
    }
}
