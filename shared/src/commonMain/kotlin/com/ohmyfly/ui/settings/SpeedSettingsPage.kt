package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SpeedMode
import com.ohmyfly.settings.SpeedSettings
import com.ohmyfly.settings.SettingsLimits
import com.ohmyfly.ui.DriftIcons

@Composable
internal fun SpeedSettingsPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, padding: PaddingValues) {
    SettingsList(padding) {
        item { SettingsPageIntro(SettingsPage.Speed.title, DriftIcons.Speed, "默认参数用于下次测试，不修改已有测量结果。") }
        item { SettingsSectionTitle("模式与目标", MirrorIcons.Globe) }
        item { SpeedTargets(settings, onChange) }
        item { SettingsSectionTitle("延迟测试", SettingsIcons.Timer) }
        item { SpeedLatency() }
        item { SettingsSectionTitle("下载与上传", SettingsIcons.Download) }
        item { SpeedTransfer(settings, onChange) }
        item { SettingsSectionTitle("达标条件与导出", SettingsIcons.Filter) }
        item { SpeedCriteria(settings, onChange) }
        item { ResetSettings(SettingsResetTarget.Speed) }
    }
}

@Composable
private fun SpeedTargets(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsCard {
        ChoiceSetting("默认模式", settings.speed.mode.name,
            listOf("Latency" to "仅延迟", "Download" to "延迟＋下载", "Full" to "延迟＋下载＋上传"), SettingsIcons.Tune) { selected ->
            onChange { current -> current.withSpeed { it.copy(mode = SpeedMode.valueOf(selected)) } }
        }
        TextSetting("延迟目标网址", { it.speed.latencyUrl }, MirrorIcons.Globe,
            keyboard = KeyboardType.Uri, validate = ::validateTargetUrl) { current, url -> current.withSpeed { it.copy(latencyUrl = url) } }
        TextSetting("下载测试服务网址", { it.speed.downloadUrl }, SettingsIcons.Download,
            hint = "上传需要兼容的服务端，不会静默降级。", keyboard = KeyboardType.Uri, validate = ::validateTargetUrl) {
            current, url -> current.withSpeed { it.copy(downloadUrl = url) }
        }
    }
}

@Composable
private fun SpeedLatency() {
    SettingsCard {
        NumberSetting("延迟测试并发数", { it.speed.latencyConcurrency }, 1..32, SettingsIcons.Material,
            "同时探测的航班数，不是采样次数。") { current, n -> current.withSpeed { it.copy(latencyConcurrency = n) } }
        NumberSetting("延迟请求超时（秒）", { it.speed.latencyTimeoutSeconds }, 1..15, SettingsIcons.Timer) {
            current, n -> current.withSpeed { it.copy(latencyTimeoutSeconds = n) }
        }
        NumberSetting("每个航班采样次数", { it.speed.latencySamples }, 1..10, SettingsIcons.Retry,
            "结果记录 HTTP 延迟，不是 ICMP Ping。") { current, n -> current.withSpeed { it.copy(latencySamples = n) } }
    }
}

@Composable
private fun SpeedTransfer(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsCard {
        NumberSetting("单个航班连接数", { it.speed.connectionsPerNode }, 1..8, MirrorIcons.Link,
            "单个航班下载／上传连接数；同时下载航班数固定为 1。") { current, n -> current.withSpeed { it.copy(connectionsPerNode = n) } }
        NumberSetting("每个航班下载量（MiB）", { it.speed.downloadMiB }, 1..500, SettingsIcons.Download) {
            current, n -> current.withSpeed { it.copy(downloadMiB = n) }
        }
        if (settings.speed.mode == SpeedMode.Full) NumberSetting("每个航班上传量（MiB）", { it.speed.uploadMiB }, 1..100, SettingsIcons.Upload) {
            current, n -> current.withSpeed { it.copy(uploadMiB = n) }
        }
        NumberSetting("传输请求超时（秒）", { it.speed.transferTimeoutSeconds }, 3..60, SettingsIcons.Timer) {
            current, n -> current.withSpeed { it.copy(transferTimeoutSeconds = n) }
        }
        ToggleSetting("蜂窝流量确认", "开始大流量航班测试前询问。", settings.speed.cellularConfirm, SettingsIcons.Haptic) {
            checked -> onChange { current -> current.withSpeed { it.copy(cellularConfirm = checked) } }
        }
    }
}

@Composable
private fun SpeedCriteria(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsCard {
        NumberSetting("最大延迟（毫秒）", { it.speed.maxLatencyMs }, 0..60000, SettingsIcons.Timer,
            "0 表示关闭筛选，不影响请求超时。") { current, n -> current.withSpeed { it.copy(maxLatencyMs = n) } }
        NumberSetting("最大 HTTP 失败率（%）", { it.speed.maxFailurePercent }, 0..100, SettingsIcons.Filter) {
            current, n -> current.withSpeed { it.copy(maxFailurePercent = n) }
        }
        DecimalSetting("最低下载速度（MiB/s）", { it.speed.minDownloadMiBps }, SettingsIcons.Download,
            "0 表示关闭；支持小数，不自动删除未达标航班。") { current, n -> current.withSpeed { it.copy(minDownloadMiBps = n) } }
        if (settings.speed.mode == SpeedMode.Full) DecimalSetting("最低上传速度（MiB/s）", { it.speed.minUploadMiBps }, SettingsIcons.Upload,
            "0 表示关闭；支持小数。") { current, n -> current.withSpeed { it.copy(minUploadMiBps = n) } }
        NumberSetting("达标后停止数量", { it.speed.stopAfter }, 0..100000, MirrorIcons.Stop,
            "0 表示测完全部；未测航班不标记失败。") { current, n -> current.withSpeed { it.copy(stopAfter = n) } }
        ToggleSetting("导出时重命名", "仅影响导出名称，不改变航班身份和历史。", settings.speed.renameExport, SettingsIcons.Rename) {
            checked -> onChange { current -> current.withSpeed { it.copy(renameExport = checked) } }
        }
    }
}

private fun AppSettings.withSpeed(change: (SpeedSettings) -> SpeedSettings): AppSettings = copy(speed = change(speed))

internal fun validateTargetUrl(value: String): String? =
    if (SettingsLimits.isValidTarget(value)) null else "请输入完整 HTTPS 网址，不能包含凭据或片段"
