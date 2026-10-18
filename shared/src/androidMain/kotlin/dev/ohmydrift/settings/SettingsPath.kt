package dev.ohmydrift.settings

import android.content.Context
import java.io.File

/** Android 设置文件路径：filesDir/datastore/settings.preferences_pb。目录由 DataStore 首次写入时创建。 */
fun androidSettingsPath(context: Context): String =
    File(File(context.filesDir, "datastore"), "settings.preferences_pb").absolutePath
