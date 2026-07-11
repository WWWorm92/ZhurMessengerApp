package com.pulsemessenger.android.feature.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsemessenger.android.ui.FullscreenImageViewer
import com.pulsemessenger.android.ui.resolveBackendMediaUrl

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showAvatarFullscreen by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(context, uri)
        }
    }

    val user = viewModel.currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Профиль", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) {
                Text("Готово")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            if (user?.avatarUrl.isNullOrBlank()) {
                DialogAvatarLarge(user?.displayName ?: "?")
            } else {
                AsyncImage(
                    model = resolveBackendMediaUrl(user?.avatarUrl.orEmpty()),
                    contentDescription = "avatar",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable { showAvatarFullscreen = true }
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .align(Alignment.BottomEnd)
                    .clickable { avatarPicker.launch("image/*") }
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.isUploading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("+", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Нажмите, чтобы изменить фото",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Имя пользователя", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("@${user?.username ?: ""}", fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.height(16.dp))

                Text("Отображаемое имя", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))

                if (viewModel.isEditing) {
                    OutlinedTextField(
                        value = viewModel.editDisplayName,
                        onValueChange = { viewModel.editDisplayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = viewModel::cancelEdit) {
                            Text("Отмена")
                        }
                        Button(onClick = viewModel::saveDisplayName) {
                            Text("Сохранить")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(user?.displayName ?: "", fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = viewModel::beginEdit) {
                            Text("Изменить")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Смена пароля", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.currentPassword,
                    onValueChange = { viewModel.currentPassword = it; viewModel.passwordChangeSuccess = false; viewModel.passwordError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текущий пароль") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.newPassword,
                    onValueChange = { viewModel.newPassword = it; viewModel.passwordChangeSuccess = false; viewModel.passwordError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.confirmPassword,
                    onValueChange = { viewModel.confirmPassword = it; viewModel.passwordChangeSuccess = false; viewModel.passwordError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Подтвердите новый пароль") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                )

                if (!viewModel.passwordError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.passwordError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (viewModel.passwordChangeSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Пароль успешно изменён",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::changePassword,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !viewModel.isChangingPassword
                ) {
                    if (viewModel.isChangingPassword) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Сменить пароль")
                    }
                }
            }
        }

        if (!viewModel.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewModel.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showAvatarFullscreen && !user?.avatarUrl.isNullOrBlank()) {
            FullscreenImageViewer(
                imageModel = resolveBackendMediaUrl(user?.avatarUrl.orEmpty()),
                onDismiss = { showAvatarFullscreen = false },
            )
        }
    }
}

@Composable
private fun DialogAvatarLarge(displayName: String) {
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF6FB2E8), Color(0xFF79D4B3)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.displaySmall
        )
    }
}
