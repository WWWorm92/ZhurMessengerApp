package com.pulsemessenger.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.ui.DialogAvatar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    var passwordTarget by remember { mutableStateOf<DialogUserDto?>(null) }
    var newPassword by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Console", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        }
                        Text("Сводка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        val stats = viewModel.adminStats
                        StatLine("Пользователи", stats?.users ?: 0)
                        StatLine("Комнаты", stats?.rooms ?: 0)
                        StatLine("ЛС сообщений", stats?.dmMessages ?: 0)
                        StatLine("Сообщений в комнатах", stats?.roomMessages ?: 0)
                        StatLine("Опросы", stats?.polls ?: 0)
                    }
                }
            }
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Создать пользователя", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = viewModel.adminUsername, onValueChange = { viewModel.adminUsername = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                        OutlinedTextField(value = viewModel.adminDisplayName, onValueChange = { viewModel.adminDisplayName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Имя в чате") }, singleLine = true)
                        OutlinedTextField(value = viewModel.adminPassword, onValueChange = { viewModel.adminPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Пароль") }, singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Сделать админом", modifier = Modifier.weight(1f))
                            Switch(checked = viewModel.adminCreateIsAdmin, onCheckedChange = { viewModel.adminCreateIsAdmin = it })
                        }
                        Button(onClick = viewModel::createAdminUser, modifier = Modifier.fillMaxWidth()) {
                            Text("Создать пользователя")
                        }
                    }
                }
            }
            item {
                Text(
                    "Пользователи",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(viewModel.adminUsers, key = { it.id }) { user ->
                            AdminUserCard(
                                user = user,
                                isActing = viewModel.adminActingUserId == user.id,
                                onToggleRole = { viewModel.toggleAdminRole(user) },
                                onResetPassword = { passwordTarget = user },
                                onDelete = { viewModel.deleteAdminUser(user.id) },
                            )
                        }

                        if (!viewModel.error.isNullOrBlank()) {
                            item {
                                Text(
                                    viewModel.error ?: "",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (passwordTarget != null) {
            AlertDialog(
                onDismissRequest = { passwordTarget = null; newPassword = "" },
                title = { Text("Сменить пароль") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(passwordTarget?.displayName ?: "")
                        OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Новый пароль") }, singleLine = true)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val target = passwordTarget ?: return@Button
                        viewModel.resetAdminPassword(target.id, newPassword)
                        passwordTarget = null
                        newPassword = ""
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    Button(onClick = { passwordTarget = null; newPassword = "" }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun AdminUserCard(
    user: DialogUserDto,
    isActing: Boolean,
    onToggleRole: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogAvatar(user.displayName, avatarUrl = user.avatarUrl)
                Spacer(modifier = Modifier.weight(1f))
                if (user.isAdmin) {
                    Text("admin", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(user.displayName, fontWeight = FontWeight.SemiBold)
            Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onToggleRole,
                    enabled = !isActing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = if (user.isAdmin) "Снять админа" else "Сделать админом",
                        tint = if (user.isAdmin) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                IconButton(
                    onClick = onResetPassword,
                    enabled = !isActing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Сменить пароль",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDelete,
                    enabled = !isActing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить пользователя",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}
