package io.tryvital.sample.ui.users

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.tryvital.client.VitalClient
import io.tryvital.sample.Screen
import io.tryvital.sample.UserRepository

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UsersScreen(
    client: VitalClient,
    @Suppress("UNUSED_PARAMETER") navController: NavHostController,
    userRepository: UserRepository
) {
    val viewModel: UsersViewModel = viewModel(
        factory = UsersViewModel.provideFactory(client, userRepository)
    )
    val openDialog = remember { mutableStateOf(false) }

    if (openDialog.value) {
        UserInputDialog(onDismiss = {
            openDialog.value = false
        }) { text ->
            viewModel.addUser(text)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.update()
    }
    val state = viewModel.uiState.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vital sample") },
                actions = {
                    IconAction(
                        painter = rememberVectorPainter(image = Icons.Default.Bluetooth),
                        description = "Add user",
                        onClick = {
                            navController.navigate(Screen.Devices.route)
                        }
                    )
                    IconAction(
                        painter = rememberVectorPainter(image = Icons.Default.Person),
                        description = "Add user",
                        onClick = {
                            openDialog.value = true
                        }
                    )
                    IconAction(
                        painter = rememberVectorPainter(image = Icons.Outlined.Refresh),
                        description = "Refresh",
                        onClick = {
                            viewModel.update()
                        }
                    )
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color(0xFFE0E0E0))
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.users != null) {
                val context = LocalContext.current
                LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
                    items(state.users.size) { index ->
                        val user = state.users[index]

                        UserListItem(
                            user = user,
                            isSelected = state.selectedUser == user,
                            onCreateLink = {
                                viewModel.linkUserWithProvider(context, it)
                            },
                            onRemove = {
                                viewModel.removeUser(it)
                            },
                            onSelect = {
                                viewModel.selectUser(it)
                            }
                        )
                    }
                }
            }

            //Not ready
//            Box(Modifier.align(Alignment.BottomCenter)) {
//                AnimatedVisibility(
//                    visible = state.selectedUser != null,
//                    enter = slideInVertically(
//                        initialOffsetY = { 40 }
//                    ) + expandVertically(
//                        expandFrom = Alignment.Bottom
//                    ) + fadeIn(initialAlpha = 0.3f),
//                    exit = slideOutVertically() + shrinkVertically() + fadeOut(),
//                ) {
//                    Card(
//                        modifier = Modifier
//                            .align(Alignment.BottomCenter)
//                            .height(80.dp)
//                            .fillMaxWidth()
//                    ) {
//                        Row(
//                            Modifier.padding(16.dp),
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Text(
//                                "Health Connect",
//                                fontSize = 18.sp,
//                                fontWeight = FontWeight.Bold,
//                            )
//                            Spacer(Modifier.weight(1f))
//                            Button(
//                                onClick = { navController.navigate(Screen.HealthConnect.route) },
//                                contentPadding = PaddingValues(
//                                    start = 20.dp,
//                                    top = 12.dp,
//                                    end = 20.dp,
//                                    bottom = 12.dp
//                                )
//                            ) {
//                                Icon(
//                                    Icons.Outlined.Sync,
//                                    contentDescription = null,
//                                    modifier = Modifier.size(ButtonDefaults.IconSize)
//                                )
//                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
//                                Text("Connect")
//                            }
//                        }
//                    }
//
//                }
//            }

        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInputDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val text = remember {
        mutableStateOf("")
    }
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(text = "User name:")
        },
        text = {
            Column {
                TextField(
                    value = text.value,
                    onValueChange = { text.value = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onSubmit(text.value)
            }) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            Button(onClick = {
                onDismiss()
            }) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
fun IconAction(painter: Painter, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
    ) {
        Icon(
            painter = painter,
            contentDescription = description,
            modifier = Modifier
                .size(24.dp)
        )
    }
}