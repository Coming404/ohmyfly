package com.ohmyfly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.ohmyfly.App
import com.ohmyfly.settings.androidSettingsPath

class MainActivity : ComponentActivity() {
    private var dynamicColorSeed by mutableStateOf<Color?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val path = androidSettingsPath(applicationContext)
        val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        val version = "${info.versionName ?: "未知版本"} (${info.longVersionCode}) · Android"
        dynamicColorSeed = Color(getColor(android.R.color.system_accent1_500))
        setContent {
            App(dynamicColorSeed = dynamicColorSeed, settingsPath = path, version = version) { dark, _ ->
                val systemBars = WindowCompat.getInsetsController(window, window.decorView)
                systemBars.isAppearanceLightStatusBars = !dark
                systemBars.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // System palette resources also change when returning from wallpaper/theme settings.
        dynamicColorSeed = Color(getColor(android.R.color.system_accent1_500))
    }
}
