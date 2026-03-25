package com.trustmebro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.trustmebro.ui.screens.MainScreen
import com.trustmebro.ui.theme.TrustMeBroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Edge-to-edge display
        enableEdgeToEdge()

        setContent {
            TrustMeBroTheme {
                MainScreen()
            }
        }
    }
}
