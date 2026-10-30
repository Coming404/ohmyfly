package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.MediaSettings

private val videoServices = listOf("netflix" to "Netflix", "disney" to "Disney+", "youtube" to "YouTube Premium", "tiktok" to "TikTok")
private val aiServices = listOf("openai" to "OpenAI", "gemini" to "Gemini", "copilot" to "Copilot")

@Composable
internal fun MediaSettingsPage(settings: AppSettings, onChange: (String, (AppSettings) -> AppSettings) -> Unit, padding: PaddingValues) {
    val media = settings.media
    fun update(name: String, change: (MediaSettings) -> MediaSettings) = onChange(name) { it.copy(media = change(it.media)) }
    SettingsList(padding) {
        item { SettingsPageIntro(SettingsPage.Media.title) }
        item {
            SettingsCard {
                ToggleSetting("启用流媒体与 AI 检测", "在航班测试后运行所选检测项目。", media.enabled, SettingsIcons.Media) {
                    checked -> update("启用流媒体与 AI 检测") { it.copy(enabled = checked) }
                }
                NumberSetting("流媒体并发数", { it.media.concurrency }, 1..8, SettingsIcons.Material,
                    "同时检测的航班数；每个航班至多 2 项平台任务。") { current, n -> current.copy(media = current.media.copy(concurrency = n)) }
                NumberSetting("单平台超时（秒）", { it.media.timeoutSeconds }, 5..30, SettingsIcons.Timer) {
                    current, n -> current.copy(media = current.media.copy(timeoutSeconds = n))
                }
                NumberSetting("网络失败重试次数", { it.media.retries }, 0..2, SettingsIcons.Retry,
                    "取消不重试，明确地区限制不重试。") { current, n -> current.copy(media = current.media.copy(retries = n)) }
                SettingsInfo("全局平台任务上限", "${minOf(media.concurrency * 2, 16)} 项；不等于 HTTP 连接数。", SettingsIcons.Material)
            }
        }
        item { SettingsSectionTitle("流媒体", SettingsIcons.Media) }
        item { SettingsCard { videoServices.forEach { (id, label) ->
            ToggleSetting(label, "检测地区可用性，不代表账号或全部片库可用。", id in media.platforms, SettingsIcons.Media) { checked ->
                update(label) { it.copy(platforms = if (checked) it.platforms + id else it.platforms - id) }
            }
        } } }
        item { SettingsSectionTitle("AI 服务", SettingsIcons.Search) }
        item { SettingsCard { aiServices.forEach { (id, label) ->
            ToggleSetting(label, "网络与地区可用性，不验证账号登录。", id in media.platforms, SettingsIcons.Search) { checked ->
                update(label) { it.copy(platforms = if (checked) it.platforms + id else it.platforms - id) }
            }
        } } }
        item { ResetSettings(SettingsResetTarget.Media) }
    }
}
