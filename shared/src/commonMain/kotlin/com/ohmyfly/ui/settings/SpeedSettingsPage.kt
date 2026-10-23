package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SpeedMode
import com.ohmyfly.settings.SpeedSettings
import com.ohmyfly.settings.SettingsLimits
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SpeedSettingsPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, padding: PaddingValues) {
    val speed = settings.speed
    fun update(change: (SpeedSettings) -> SpeedSettings) = onChange { it.copy(speed = change(it.speed)) }
    SettingsList(padding) {
        item { BasicComponent(title = "测试默认参数", summary = "默认值用于下次测试，不修改已有结果的测量条件。") }
        item { SmallTitle("模式与目标") }
        item {
            SettingsCard {
                ChoiceSetting("默认模式", speed.mode.name, listOf("Latency" to "仅延迟", "Download" to "延迟＋下载", "Full" to "延迟＋下载＋上传")) {
                    selected -> update { it.copy(mode = SpeedMode.valueOf(selected)) }
                }
                TextSetting("延迟目标网址", speed.latencyUrl, keyboard = KeyboardType.Uri, validate = ::validateTargetUrl) { url -> update { it.copy(latencyUrl = url) } }
                TextSetting("下载测试服务网址", speed.downloadUrl, "上传需要兼容的服务端，不会静默降级。", KeyboardType.Uri, ::validateTargetUrl) { url -> update { it.copy(downloadUrl = url) } }
            }
        }
        item { SmallTitle("延迟测试") }
        item {
            SettingsCard {
                NumberSetting("延迟测试并发数", speed.latencyConcurrency, 1..32, "同时探测的航班数，不是采样次数。") { n -> update { it.copy(latencyConcurrency = n) } }
                NumberSetting("延迟请求超时（秒）", speed.latencyTimeoutSeconds, 1..15) { n -> update { it.copy(latencyTimeoutSeconds = n) } }
                NumberSetting("每个航班采样次数", speed.latencySamples, 1..10, "结果记录 HTTP 延迟，不是 ICMP Ping。") { n -> update { it.copy(latencySamples = n) } }
            }
        }
        item { SmallTitle("下载与上传") }
        item {
            SettingsCard {
                NumberSetting("单个航班连接数", speed.connectionsPerNode, 1..8, "单个航班下载／上传连接数；同时下载航班数固定为 1。") { n -> update { it.copy(connectionsPerNode = n) } }
                NumberSetting("每个航班下载量（MiB）", speed.downloadMiB, 1..500) { n -> update { it.copy(downloadMiB = n) } }
                if (speed.mode == SpeedMode.Full) NumberSetting("每个航班上传量（MiB）", speed.uploadMiB, 1..100) { n -> update { it.copy(uploadMiB = n) } }
                NumberSetting("传输请求超时（秒）", speed.transferTimeoutSeconds, 3..60) { n -> update { it.copy(transferTimeoutSeconds = n) } }
                ToggleSetting("蜂窝流量确认", "开始大流量航班测试前询问。", speed.cellularConfirm) { checked -> update { it.copy(cellularConfirm = checked) } }
            }
        }
        item { SmallTitle("达标条件与导出") }
        item {
            SettingsCard {
                NumberSetting("最大延迟（毫秒）", speed.maxLatencyMs, 0..60000, "0 表示关闭筛选，不影响请求超时。") { n -> update { it.copy(maxLatencyMs = n) } }
                NumberSetting("最大 HTTP 失败率（%）", speed.maxFailurePercent, 0..100) { n -> update { it.copy(maxFailurePercent = n) } }
                DecimalSetting("最低下载速度（MiB/s）", speed.minDownloadMiBps, "0 表示关闭；可输入 0.125 等小数。不自动删除未达标航班。") { n -> update { it.copy(minDownloadMiBps = n) } }
                if (speed.mode == SpeedMode.Full) DecimalSetting("最低上传速度（MiB/s）", speed.minUploadMiBps, "0 表示关闭；支持小数。") { n -> update { it.copy(minUploadMiBps = n) } }
                NumberSetting("达标后停止数量", speed.stopAfter, 0..100000, "0 表示测完全部；未测航班不标记失败。") { n -> update { it.copy(stopAfter = n) } }
                ToggleSetting("导出时重命名", "仅影响导出名称，不改变航班身份和历史。", speed.renameExport) { checked -> update { it.copy(renameExport = checked) } }
            }
        }
        item { ResetSettings { onChange { it.copy(speed = SpeedSettings()) } } }
    }
}

internal fun validateTargetUrl(value: String): String? =
    if (SettingsLimits.isValidTarget(value)) null else "请输入完整 HTTPS 网址，不能包含凭据或片段"
