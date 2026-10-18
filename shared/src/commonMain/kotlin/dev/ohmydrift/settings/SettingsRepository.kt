package dev.ohmydrift.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/**
 * 单一设置仓库。DataStore.edit 自带事务锁，读取-变换-写回在同一事务内完成。
 * 校验失败抛 IllegalArgumentException；读写异常原样上抛，不以默认值覆盖原文件。
 */
internal class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val data: Flow<AppSettings> = dataStore.data
        .map { SettingsCodec.decode(it) }
        .distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val next = transform(SettingsCodec.decode(preferences))
            val error = validateSettings(next)
            require(error == null) { error.orEmpty() }
            SettingsCodec.write(preferences, next)
        }
    }
}

/** 进程级默认实例缓存：Activity 重建或重复 composition 共用同一 DataStore。 */
internal val settingsRepositories = RepositoryCache<SettingsRepository>()

/**
 * 不预先创建目录：DataStore 首次写入时自行建父目录，首次读取缺失文件返回空偏好。
 * 这样路径计算不会在 Compose 之前抛错；真实的磁盘问题会从 [SettingsRepository.data] 上抛给 UI。
 */
internal fun createSettingsRepository(path: String): SettingsRepository {
    val file = path.toPath()
    check(file.isAbsolute) { "设置存储路径必须是绝对路径" }
    return settingsRepositories.getOrPut(file.normalized().toString()) {
        SettingsRepository(PreferenceDataStoreFactory.createWithPath(produceFile = { file }))
    }
}
