package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import dev.ohmydrift.settings.AppSettings
import dev.ohmydrift.settings.SubscriptionSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SubscriptionSettingsPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, padding: PaddingValues) {
    val subscription = settings.subscription
    fun update(change: (SubscriptionSettings) -> SubscriptionSettings) = onChange { it.copy(subscription = change(it.subscription)) }
    SettingsList(padding) {
        item { BasicComponent(title = "订阅默认参数", summary = "管理请求限制与过滤偏好，不向第三方转换服务上传订阅。") }
        item { SmallTitle("请求") }
        item {
            SettingsCard {
                NumberSetting("单次请求超时（秒）", subscription.timeoutSeconds, 3..120) { n -> update { it.copy(timeoutSeconds = n) } }
                NumberSetting("失败重试次数", subscription.retries, 0..5) { n -> update { it.copy(retries = n) } }
                NumberSetting("单份订阅上限（MiB）", subscription.maxMiB, 1..50) { n -> update { it.copy(maxMiB = n) } }
                TextSetting("User-Agent", subscription.userAgent, "留空使用核心默认值。", validate = { value ->
                    if (value.length > 256 || value.any { it.code < 32 || it.code == 127 }) "最多 256 字符，不能包含换行或控制字符" else null
                }) { value -> update { it.copy(userAgent = value) } }
            }
        }
        item { SmallTitle("名称与协议过滤") }
        item {
            SettingsCard {
                TextSetting("名称包含正则", subscription.includePattern, "留空不限制；规则将在导入核心再次验证。", validate = ::validatePattern) { value -> update { it.copy(includePattern = value) } }
                TextSetting("名称排除正则", subscription.excludePattern, "留空不排除；只影响测试范围，不删除原始订阅。", validate = ::validatePattern) { value -> update { it.copy(excludePattern = value) } }
                TextSetting("协议范围", subscription.protocols.sorted().joinToString(","), "逗号分隔协议名称；留空表示全部受支持协议。", validate = { value ->
                    if (value.length > 256 || value.split(',').any { it.isNotBlank() && !it.trim().matches(Regex("[a-zA-Z0-9-]{1,24}")) }) "协议仅允许字母、数字、连字符，以逗号分隔" else null
                }) { value -> update { it.copy(protocols = value.split(',').map { token -> token.trim().lowercase() }.filter(String::isNotEmpty).toSet()) } }
            }
        }
        item {
            SettingsCard {
                BasicComponent(title = "配置等价去重", summary = "忽略显示名称，完整比较协议、凭据、传输和 TLS 参数。不同凭据不因同一端点被删除。")
                BasicComponent(title = "刷新失败保留旧目录", summary = "单节点错误隔离；无效节点计数，不中断整份订阅。")
            }
        }
        item { ResetSettings { onChange { it.copy(subscription = SubscriptionSettings()) } } }
    }
}

internal fun validatePattern(value: String): String? = when {
    value.length > 512 -> "正则表达式最多 512 字符"
    value.isEmpty() -> null
    else -> try { Regex(value); null } catch (_: IllegalArgumentException) { "正则表达式格式无效" }
}
