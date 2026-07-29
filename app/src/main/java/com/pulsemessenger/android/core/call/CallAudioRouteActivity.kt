package com.pulsemessenger.android.core.call

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.ui.theme.PulseAndroidTheme

class CallAudioRouteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseAndroidTheme {
                AudioRouteScreen(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun AudioRouteScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    var refreshKey by remember { mutableStateOf(0) }
    val routes = remember(refreshKey) { CallAudioRouteController.availableRoutes(context) }

    DisposableEffect(Unit) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { refreshKey++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { refreshKey++ }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { runCatching { audioManager.unregisterAudioDeviceCallback(callback) } }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Вывод звука", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Устройство можно менять во время разговора", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        routes.forEach { route ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    CallAudioRouteController.selectRoute(context, route.id)
                    refreshKey++
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = route.selected,
                    onClick = {
                        CallAudioRouteController.selectRoute(context, route.id)
                        refreshKey++
                    },
                )
                Text(route.label, modifier = Modifier.padding(start = 10.dp), fontWeight = if (route.selected) FontWeight.SemiBold else FontWeight.Normal)
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(22.dp))
        Text("Готово", modifier = Modifier.clickable(onClick = onClose).padding(12.dp), color = MaterialTheme.colorScheme.primary)
    }
}
