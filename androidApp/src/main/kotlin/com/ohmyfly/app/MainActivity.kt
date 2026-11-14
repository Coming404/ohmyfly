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
import com.ohmyfly.logging.flushAppLogsInBackground
import com.ohmyfly.logging.initializeAppLogging
import com.ohmyfly.settings.androidSettingsPath

class MainActivity : ComponentActivity() {
    private var dynamicColorSeed by mutableStateOf<Color?>(null)
    private val subscriptionFiles = AndroidSubscriptionFilePicker(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeAppLogging(applicationContext)
        com.ohmyfly.catalog.initializeCatalog(applicationContext)
        com.ohmyfly.ui.initializeCatalogFileSharing { text -> shareCatalogYaml(text) }
        enableEdgeToEdge()
        val path = androidSettingsPath(applicationContext)
        val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        val version = "${info.versionName ?: "未知版本"} (${info.longVersionCode}) · Android"
        dynamicColorSeed = Color(getColor(android.R.color.system_accent1_500))
        setContent {
            App(dynamicColorSeed = dynamicColorSeed, settingsPath = path, version = version, filePicker = subscriptionFiles) { dark, _ ->
                val systemBars = WindowCompat.getInsetsController(window, window.decorView)
                systemBars.isAppearanceLightStatusBars = !dark
                systemBars.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    override fun onDestroy() {
        com.ohmyfly.ui.initializeCatalogFileSharing(null)
        super.onDestroy()
    }

    override fun onStop() {
        com.ohmyfly.catalog.releaseCatalogPages()
        flushAppLogsInBackground()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        com.ohmyfly.catalog.resumeCatalogPages()
        // System palette resources also change when returning from wallpaper/theme settings.
        dynamicColorSeed = Color(getColor(android.R.color.system_accent1_500))
    }
}
