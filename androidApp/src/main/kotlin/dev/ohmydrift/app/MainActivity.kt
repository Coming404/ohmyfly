package dev.ohmydrift.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.ohmydrift.App
import dev.ohmydrift.settings.androidSettingsPath

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val path = androidSettingsPath(applicationContext)
        val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        val version = "${info.versionName ?: "未知版本"} (${info.longVersionCode}) · Android"
        setContent { App(useSystemDynamicColors = true, settingsPath = path, version = version) }
    }
}
