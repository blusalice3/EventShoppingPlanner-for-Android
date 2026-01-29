package com.example.eventshoppingplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.eventshoppingplanner.data.preferences.ThemeMode
import com.example.eventshoppingplanner.data.preferences.ThemePreference
import com.example.eventshoppingplanner.presentation.navigation.AppNavigation
import com.example.eventshoppingplanner.ui.theme.EventShoppingPlannerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePreference = ThemePreference(applicationContext)

        setContent {
            val themeMode by themePreference.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            EventShoppingPlannerTheme(themeMode = themeMode) {
                AppNavigation()
            }
        }
    }
}