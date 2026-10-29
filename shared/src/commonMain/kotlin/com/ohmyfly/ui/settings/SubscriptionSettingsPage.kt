package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.ohmyfly.settings.AppSettings
import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.ui.DriftIcons

@Composable
internal fun SubscriptionSettingsPage(padding: PaddingValues) {
    SettingsList(padding) {
        item { SettingsPageIntro(SettingsPage.Subscription.title, DriftIcons.Subscriptions,
            "塔台链接不会上传到第三方转换服务。") }
        item { SettingsSectionTitle("请求", MirrorIcons.Globe) }
        item {
            SettingsCard {
                NumberSetting("单次请求超时（秒）", { it.subscription.timeoutSeconds }, 3..120, SettingsIcons.Timer) {
                    current, n -> current.withSubscription { it.copy(timeoutSeconds = n) }
                }
                NumberSetting("失败重试次数", { it.subscription.retries }, 0..5, SettingsIcons.Retry) {
                    current, n -> current.withSubscription { it.copy(retries = n) }
                }
                NumberSetting("单个塔台来源上限（MiB）", { it.subscription.maxMiB }, 1..50, SettingsIcons.Download) {
                    current, n -> current.withSubscription { it.copy(maxMiB = n) }
                }
                TextSetting("User-Agent", { it.subscription.userAgent }, SettingsIcons.Haptic,
                    hint = "留空使用核心默认值。", validate = ::validateUserAgent) {
                    current, value -> current.withSubscription { it.copy(userAgent = value) }
                }
            }
        }
        item { SettingsSectionTitle("名称与协议过滤", SettingsIcons.Filter) }
        item {
            SettingsCard {
                TextSetting("名称包含正则", { it.subscription.includePattern }, SettingsIcons.Search,
                    hint = "留空不限制；规则将在导入核心再次验证。", validate = ::validatePattern) {
                    current, value -> current.withSubscription { it.copy(includePattern = value) }
                }
                TextSetting("名称排除正则", { it.subscription.excludePattern }, SettingsIcons.Filter,
                    hint = "留空不排除；只影响测试范围，不删除原始塔台链接。", validate = ::validatePattern) {
                    current, value -> current.withSubscription { it.copy(excludePattern = value) }
                }
                TextSetting("协议范围", { it.subscription.protocols.sorted().joinToString(",") }, MirrorIcons.Link,
                    hint = "逗号分隔协议名称；留空表示全部受支持协议。", validate = ::validateProtocols) {
                    current, value -> current.withSubscription { it.copy(protocols = value.split(',')
                        .map { token -> token.trim().lowercase() }.filter(String::isNotEmpty).toSet()) }
                }
            }
        }
        item {
            SettingsCard {
                SettingsInfo("配置等价去重", "完整比较协议、凭据、传输和 TLS 参数，不按显示名称去重。", SettingsIcons.Copy)
                SettingsInfo("刷新失败保留旧目录", "隔离单个航班错误，不中断整份塔台。", SettingsIcons.Backup)
            }
        }
        item { ResetSettings(SettingsResetTarget.Subscription) }
    }
}

private fun AppSettings.withSubscription(change: (SubscriptionSettings) -> SubscriptionSettings): AppSettings =
    copy(subscription = change(subscription))

internal fun validateUserAgent(value: String): String? =
    if (value.length > 256 || value.any { it.code < 32 || it.code == 127 }) "最多 256 字符，不能包含换行或控制字符" else null

internal fun validateProtocols(value: String): String? =
    if (value.length > 256 || value.split(',').any { it.isNotBlank() && !it.trim().matches(Regex("[a-zA-Z0-9-]{1,24}")) })
        "协议仅允许字母、数字、连字符，以逗号分隔" else null

internal fun validatePattern(value: String): String? = when {
    value.length > 512 -> "正则表达式最多 512 字符"
    value.isEmpty() -> null
    else -> try { Regex(value); null } catch (_: IllegalArgumentException) { "正则表达式格式无效" }
}
