package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.settings.MediaSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

private val videoServices = listOf("netflix" to "Netflix", "disney" to "Disney+", "youtube" to "YouTube Premium", "tiktok" to "TikTok")
private val aiServices = listOf("openai" to "OpenAI", "gemini" to "Gemini", "copilot" to "Copilot")

@Composable
internal fun MediaSettingsPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, padding: PaddingValues) {
    val media = settings.media
    fun update(change: (MediaSettings) -> MediaSettings) = onChange { it.copy(media = change(it.media)) }
    SettingsList(padding) {
        item { BasicComponent(title = "检测偏好", summary = "为后续节点任务选择默认项目与请求限制。") }
        item {
            SettingsCard {
                ToggleSetting("启用流媒体与 AI 检测", "在节点测试后运行所选检测项目。", media.enabled) { checked -> update { it.copy(enabled = checked) } }
                NumberSetting("流媒体并发数", media.concurrency, 1..8, "同时检测的节点数；每节点至多 2 项平台任务。") { n -> update { it.copy(concurrency = n) } }
                NumberSetting("单平台超时（秒）", media.timeoutSeconds, 5..30) { n -> update { it.copy(timeoutSeconds = n) } }
                NumberSetting("网络失败重试次数", media.retries, 0..2, "取消不重试，明确地区限制不重试。") { n -> update { it.copy(retries = n) } }
                BasicComponent(title = "全局平台任务上限", summary = "${minOf(media.concurrency * 2, 16)} 项；不等于 HTTP 连接数。")
            }
        }
        item { SmallTitle("流媒体") }
        item { SettingsCard { videoServices.forEach { (id, label) ->
            ToggleSetting(label, "检测地区可用性，不代表账号或全部片库可用。", id in media.platforms) { checked ->
                update { it.copy(platforms = if (checked) it.platforms + id else it.platforms - id) }
            }
        } } }
        item { SmallTitle("AI 服务") }
        item { SettingsCard { aiServices.forEach { (id, label) ->
            ToggleSetting(label, "网络与地区可用性，不验证账号登录。", id in media.platforms) { checked ->
                update { it.copy(platforms = if (checked) it.platforms + id else it.platforms - id) }
            }
        } } }
        item { ResetSettings { onChange { it.copy(media = MediaSettings()) } } }
    }
}
