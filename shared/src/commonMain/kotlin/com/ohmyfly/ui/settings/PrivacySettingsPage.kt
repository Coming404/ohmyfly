package com.ohmyfly.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun PrivacySettingsPage(padding: PaddingValues) {
    SettingsList(padding) {
        item { SettingsPageIntro(SettingsPage.Privacy.title, SettingsIcons.Lock) }
        item { SettingsSectionTitle("本地配置", SettingsIcons.Lock) }
        item { SettingsCard {
            SettingsInfo("应用私有存储", "设置仅保存在本机沙盒，文件不加密，不保存塔台令牌或代理密码。", SettingsIcons.Lock)
            SettingsInfo("设备备份", "Android 已关闭应用备份；iOS 的 Application Support 可能进入系统备份。卸载会删除本地配置。", SettingsIcons.Backup)
        } }
        item { SettingsSectionTitle("镜像与网络", MirrorIcons.Globe) }
        item { SettingsCard {
            SettingsInfo("只在主动测试时联网", "第三方镜像可能看到设备 IP 和公开资源路径。", MirrorIcons.Globe)
            SettingsInfo("不转发凭据", "探测仅使用固定公开资源，不携带令牌、塔台凭据、Cookie 或 Authorization。", SettingsIcons.Lock)
            SettingsInfo("内容校验不等于信任", "样本哈希匹配不证明运营方可信，仍需独立验证实际下载资源的签名。", MirrorIcons.Shield)
        } }
        item { SettingsSectionTitle("恢复默认", SettingsIcons.Reset) }
        item { ResetSettings(SettingsResetTarget.All) }
    }
}
