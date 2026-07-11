package com.pulsemessenger.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pulsemessenger.android.ui.PulseAndroidApp
import com.pulsemessenger.android.ui.theme.PulseAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseAndroidTheme {
                PulseAndroidApp()
            }
        }
    }
}
