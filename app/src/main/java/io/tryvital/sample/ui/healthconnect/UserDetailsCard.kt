package io.tryvital.sample.ui.healthconnect

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tryvital.sample.ui.users.IconAction

@Composable
fun UserDetailsCard(
    state: HealthConnectViewModelState,
    viewModel: HealthConnectViewModel
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    Card(Modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Selected User Details", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val userId = state.user.userId

                Column {
                    Text("User id")
                    Text(
                        userId,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconAction(
                    painter = rememberVectorPainter(image = Icons.Default.ContentCopy),
                    description = "Copy User id",
                ) {
                    clipboardManager.setText(AnnotatedString(userId))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Column {
                    Text("Current SDK User")
                    Text(
                        if (state.isCurrentSDKUser) "Yes" else "No",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val clientUserId = state.user.clientUserId

                Column {
                    Text("User Client Id")
                    Text(
                        clientUserId,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconAction(
                    painter = rememberVectorPainter(image = Icons.Default.ContentCopy),
                    description = "Copy Client User id",
                ) {
                    clipboardManager.setText(AnnotatedString(clientUserId))

                }
            }
        }
    }

}