package com.ohmyfly.settings

/** 存储文件由更新版本写入时明确失败，避免旧代码用默认值覆盖新字段。 */
internal class SettingsSchemaException(
    val foundVersion: Int,
    val supportedVersion: Int,
) : IllegalStateException(
    "设置文件版本为 $foundVersion，高于当前支持的 $supportedVersion；请升级应用后再修改设置。",
)
