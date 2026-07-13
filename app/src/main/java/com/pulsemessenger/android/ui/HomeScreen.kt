package com.pulsemessenger.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.luminance
enum class HomeTab { Dialogs, Rooms }
private val DrawerBlue = Color(0xFF2196F3)
private val DrawerBlueDarkContainer = Color(0xFF163B5C)
private val DrawerBlueLightContainer = Color(0xFFE3F2FD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUserName: String,
    currentUserSubtitle: String,
    currentUserAvatarUrl: String,
    isAdmin: Boolean,
    currentTab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenSearchDialogs: () -> Unit,
    onOpenMessageSearch: () -> Unit,
    onOpenCreateRoom: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit,
    invitationsCount: Int,
    dialogsContent: @Composable () -> Unit,
    roomsContent: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val title = when (currentTab) {
        HomeTab.Dialogs -> "Диалоги"
        HomeTab.Rooms -> "Комнаты"
    }
    val drawerIsOpen =
        drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.34f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(332.dp),
                drawerContainerColor = Color.Transparent,
                drawerTonalElevation = 0.dp,
                windowInsets = WindowInsets.safeDrawing
            ) {
                GlassDrawerSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 10.dp)
                ) {
                    DrawerProfileHero(
                        currentUserName = currentUserName,
                        currentUserSubtitle = currentUserSubtitle,
                        currentUserAvatarUrl = currentUserAvatarUrl,
                        onClick = {
                            closeDrawer()
                            onOpenProfile()
                        }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    DrawerGlassItem(
                        icon = Icons.Filled.QuestionAnswer,
                        title = "Диалоги",
                        selected = currentTab == HomeTab.Dialogs,
                        onClick = {
                            onTabChange(HomeTab.Dialogs)
                            closeDrawer()
                        }
                    )

                    DrawerGlassItem(
                        icon = Icons.Filled.Groups,
                        title = "Комнаты",
                        selected = currentTab == HomeTab.Rooms,
                        onClick = {
                            onTabChange(HomeTab.Rooms)
                            closeDrawer()
                        }
                    )

                    DrawerGlassItem(
                        icon = Icons.Filled.MailOutline,
                        title = "Приглашения",
                        badge = invitationsCount.takeIf { it > 0 }?.toString(),
                        onClick = {
                            closeDrawer()
                            onOpenInvitations()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))


                    DrawerGlassItem(
                        icon = Icons.Filled.Settings,
                        title = "Настройки",
                        onClick = {
                            closeDrawer()
                            onOpenSettings()
                        }
                    )

                    if (isAdmin) {
                        DrawerGlassItem(
                            icon = Icons.Filled.Settings,
                            title = "Консоль администратора",
                            onClick = {
                                closeDrawer()
                                onOpenAdmin()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    DrawerGlassItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "Выйти",
                        danger = true,
                        onClick = {
                            closeDrawer()
                            onLogout()
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.blur(if (drawerIsOpen) 18.dp else 0.dp),
            topBar = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню")
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = when (currentTab) {
                                    HomeTab.Dialogs -> "Личные сообщения"
                                    HomeTab.Rooms -> "Комнаты и обсуждения"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onOpenMessageSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }

                        IconButton(
                            onClick = {
                                if (currentTab == HomeTab.Dialogs) {
                                    onOpenSearchDialogs()
                                } else {
                                    onOpenCreateRoom()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == HomeTab.Dialogs) {
                                    Icons.Filled.PersonAdd
                                } else {
                                    Icons.Filled.Add
                                },
                                contentDescription = if (currentTab == HomeTab.Dialogs) {
                                    "Новый диалог"
                                } else {
                                    "Создать комнату"
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (currentTab == HomeTab.Dialogs) {
                    dialogsContent()
                } else {
                    roomsContent()
                }
            }
        }
    }
}
@Composable
private fun GlassDrawerSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.14f)
                    ),
                    shape
                )
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(34f, 34f, Shader.TileMode.DECAL)
                                .asComposeRenderEffect()
                        }
                    } else {
                        Modifier
                    }
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            content = content
        )
    }
}

@Composable
private fun DrawerProfileHero(
    currentUserName: String,
    currentUserSubtitle: String,
    currentUserAvatarUrl: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialogAvatar(
                currentUserName,
                avatarUrl = currentUserAvatarUrl,
                modifier = Modifier.size(54.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentUserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = currentUserSubtitle.ifBlank { "Открыть профиль" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Card(
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DrawerBlue.copy(alpha = 0.22f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Профиль",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 2.dp)
    )
}

@Composable
private fun DrawerGlassItem(
    icon: ImageVector,
    title: String,
    selected: Boolean = false,
    danger: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val selectedContainer = if (isLightTheme) {
        DrawerBlueLightContainer.copy(alpha = 0.96f)
    } else {
        DrawerBlueDarkContainer.copy(alpha = 0.92f)
    }

    val normalContainer = if (isLightTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    }

    val containerColor = when {
        danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
        selected -> selectedContainer
        else -> normalContainer
    }

    val borderColor = when {
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
        selected -> DrawerBlue.copy(alpha = if (isLightTheme) 0.34f else 0.38f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = if (isLightTheme) 0.14f else 0.10f)
    }

    val iconColor = when {
        danger -> MaterialTheme.colorScheme.error
        selected -> DrawerBlue
        else -> MaterialTheme.colorScheme.onSurface
    }

    val titleColor = when {
        danger -> MaterialTheme.colorScheme.error
        selected -> if (isLightTheme) DrawerBlue else MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            if (!badge.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(50.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DrawerBlue
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(DrawerBlue)
                )
            }
        }
    }
}