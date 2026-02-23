package io.tryvital.sample.ui.healthconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.ProviderAvailability

@Composable
fun SamsungReadDataCard(
    state: SamsungHealthViewModelState,
    viewModel: SamsungHealthViewModel,
) {
    if (state.available != ProviderAvailability.Installed) {
        return
    }

    Card(Modifier.padding(16.dp)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Read data", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(text = state.syncStatus)
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(
                    onClick = { viewModel.sync() },
                    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Healing,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Sync")
                }
            }
        }

        Box(modifier = Modifier.height(16.dp))
        Text("Print data to console")
        VitalResource.values().filter(::isSupportedBySamsungDataApi).forEach {
            SamsungResourceReaderButton(viewModel, it)
        }
        Box(modifier = Modifier.height(16.dp))
    }
}

private fun isSupportedBySamsungDataApi(resource: VitalResource): Boolean = when (resource) {
    VitalResource.HeartRateVariability -> false
    VitalResource.MenstrualCycle -> false
    VitalResource.RespiratoryRate -> false
    VitalResource.Meal -> false
    else -> true
}

@Composable
private fun SamsungResourceReaderButton(
    viewModel: SamsungHealthViewModel,
    resource: VitalResource,
) {
    Button(
        onClick = { viewModel.readResource(resource) },
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
    ) {
        Text("Read ${resource.name}")
    }
}
