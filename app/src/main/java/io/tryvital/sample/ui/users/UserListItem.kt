package io.tryvital.sample.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.tryvital.client.services.data.User
import io.tryvital.sample.Screen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserListItem(
    user: User,
    sdkUserId: String?,
    onCreateLink: (User) -> Unit,
    onRemove: (User) -> Unit,
    navController: NavController,
) {
    val iconColor = Color.Gray
    val textColor = Color.DarkGray
    val backgroundColor = Color.Transparent

    Row(
        modifier = Modifier
            .background(color = backgroundColor)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            painter = rememberVectorPainter(image = Icons.Default.Person),
            contentDescription = "Person",
            tint = iconColor
        )
        Spacer(modifier = Modifier.width(12.dp))


        Text(
            text = user.clientUserId ?: "",
            style = TextStyle(fontSize = 18.sp, color = textColor),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = {
                onRemove(user)
            },
        ) {
            Icon(
                painter = rememberVectorPainter(image = Icons.Default.Delete),
                contentDescription = "Remove",
                tint = iconColor
            )
        }
    }
    if (sdkUserId == user.userId) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = rememberVectorPainter(image = Icons.Default.CheckCircle),
                contentDescription = "Current SDK User",
                tint = iconColor
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Current SDK User")
        }

        FlowRow(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            OutlinedButton(
                onClick = { onCreateLink(user) },
            ) {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Default.Sync),
                    contentDescription = "Link provider",
                    tint = iconColor
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Vital Link")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = { navController.navigate(Screen.HealthConnect.route) }
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Health Connect")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = { navController.navigate(Screen.SamsungHealth.route) }
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Samsung Health")
            }
        }
    }
}
