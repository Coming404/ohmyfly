package dev.ohmydrift.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import dev.ohmydrift.settings.AppSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun PrivacySettingsPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, padding: PaddingValues) {
    SettingsList(padding) {
        item { SmallTitle("本地配置") }
        item { SettingsCard {
            BasicComponent(title = "应用私有存储", summary = "测速、流媒体、外观和镜像配置仅保存在本机应用沙盒。普通设置文件不加密，不保存订阅令牌或代理密码。")
            BasicComponent(title = "设备备份", summary = "Android 已关闭应用备份；iOS 的 Application Support 可能进入系统备份。卸载应用会删除本地配置。")
        } }
        item { SmallTitle("镜像与网络") }
        item { SettingsCard {
            BasicComponent(title = "只在主动测试时联网", summary = "进入页面或添加镜像不会自动探测。第三方镜像可能看到设备 IP 和公开资源路径。")
            BasicComponent(title = "不转发凭据", summary = "镜像探测使用固定公开资源，不携带 GitHub Token、订阅凭据、Cookie 或 Authorization。")
            BasicComponent(title = "内容校验不等于信任", summary = "样本哈希匹配不证明运营方可信，也不替代实际下载资源的独立签名验证。")
        } }
        item { SmallTitle("恢复默认") }
        item {
            ResetSettings("恢复全部设置") {
                onChange { current -> AppSettings(mirrors = current.mirrors, selectedMirrorId = current.selectedMirrorId) }
            }
        }
    }
}
