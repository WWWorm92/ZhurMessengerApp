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

enum class HomeTab { Dialogs, Rooms }

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

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable {
                                closeDrawer()
                                onOpenProfile()
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DialogAvatar(currentUserName, avatarUrl = currentUserAvatarUrl, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currentUserName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (currentUserSubtitle.isNotBlank()) {
                                    Text(
                                        currentUserSubtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.QuestionAnswer, contentDescription = null) },
                        label = { Text("Диалоги", fontWeight = FontWeight.Medium) },
                        selected = currentTab == HomeTab.Dialogs,
                        onClick = {
                            onTabChange(HomeTab.Dialogs)
                            closeDrawer()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        )
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                        label = { Text("Комнаты", fontWeight = FontWeight.Medium) },
                        selected = currentTab == HomeTab.Rooms,
                        onClick = {
                            onTabChange(HomeTab.Rooms)
                            closeDrawer()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        )
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.MailOutline, contentDescription = null) },
                        label = {
                            if (invitationsCount > 0) {
                                BadgedBox(badge = { Badge { Text("$invitationsCount") } }) {
                                    Text("Приглашения", fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Text("Приглашения", fontWeight = FontWeight.Medium)
                            }
                        },
                        selected = false,
                        onClick = {
                            closeDrawer()
                            onOpenInvitations()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        )
                    )

                    /*NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Поиск сообщений", fontWeight = FontWeight.Medium) },
                        selected = false,
                        onClick = {
                            closeDrawer()
                            onOpenMessageSearch()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        )
                    )*/

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Настройки", fontWeight = FontWeight.Medium) },
                        selected = false,
                        onClick = {
                            closeDrawer()
                            onOpenSettings()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        )
                    )

                    if (isAdmin) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text("Консоль администратора", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                closeDrawer()
                                onOpenAdmin()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                        label = { Text("Выйти", fontWeight = FontWeight.Medium) },
                        selected = false,
                        onClick = {
                            closeDrawer()
                            onLogout()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.error,
                            unselectedTextColor = MaterialTheme.colorScheme.error,
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (currentTab) {
                                HomeTab.Dialogs -> Icons.Filled.QuestionAnswer
                                HomeTab.Rooms -> Icons.Filled.Groups
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenMessageSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        if (currentTab == HomeTab.Dialogs) {
                            IconButton(onClick = onOpenSearchDialogs) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = "Новый диалог")
                            }
                        } else {
                            IconButton(onClick = onOpenCreateRoom) {
                                Icon(Icons.Filled.Add, contentDescription = "Создать комнату")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    )
                )
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
