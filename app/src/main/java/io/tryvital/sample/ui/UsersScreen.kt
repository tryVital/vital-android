package io.tryvital.sample.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tryvital.client.VitalClient
import io.tryvital.sample.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UsersScreen(client: VitalClient) {
    val viewModel: UsersViewModel = viewModel(
        factory = UsersViewModel.provideFactory(client)
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
                        painter = painterResource(id = R.drawable.ic_baseline_person_add_24),
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
        Box(modifier = Modifier.padding(padding)) {
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

                        UserRow(
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