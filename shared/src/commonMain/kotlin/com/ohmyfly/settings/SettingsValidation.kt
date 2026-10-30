package com.ohmyfly.settings

import com.ohmyfly.mirror.validateMirror
import com.ohmyfly.mirror.normalizedMirrorAddress

/**
 * 返回 null 表示合法；错误信息为中文，不回显地址、UA 等可能携带凭据的原值。
 * 数值范围与 [SettingsLimits] 共用，避免校验/解码漂移。
 */
internal fun validateSettings(settings: AppSettings): String? {
    val speed = settings.speed
    if (!SettingsLimits.isValidTarget(speed.latencyUrl)) return "延迟测试地址必须是 HTTPS 地址，且不能包含用户信息或凭据参数"
    if (!SettingsLimits.isValidTarget(speed.downloadUrl)) return "下载测试地址必须是 HTTPS 地址，且不能包含用户信息或凭据参数"
    if (speed.downloadMiB !in SettingsLimits.downloadMiB) return "下载测试体积需在 ${SettingsLimits.downloadMiB.rangeText()} MiB"
    if (speed.uploadMiB !in SettingsLimits.uploadMiB) return "上传测试体积需在 ${SettingsLimits.uploadMiB.rangeText()} MiB"
    if (speed.transferTimeoutSeconds !in SettingsLimits.transferTimeoutSeconds) {
        return "传输超时需在 ${SettingsLimits.transferTimeoutSeconds.rangeText()} 秒"
    }
    if (speed.latencyConcurrency !in SettingsLimits.latencyConcurrency) {
        return "延迟并发需在 ${SettingsLimits.latencyConcurrency.rangeText()}"
    }
    if (speed.latencyTimeoutSeconds !in SettingsLimits.latencyTimeoutSeconds) {
        return "延迟超时需在 ${SettingsLimits.latencyTimeoutSeconds.rangeText()} 秒"
    }
    if (speed.latencySamples !in SettingsLimits.latencySamples) {
        return "延迟采样次数需在 ${SettingsLimits.latencySamples.rangeText()}"
    }
    if (speed.connectionsPerNode !in SettingsLimits.connectionsPerNode) {
        return "单个航班连接数需在 ${SettingsLimits.connectionsPerNode.rangeText()}"
    }
    if (speed.maxLatencyMs !in SettingsLimits.maxLatencyMs) return "延迟上限需在 0–60000 毫秒"
    if (speed.maxFailurePercent !in SettingsLimits.maxFailurePercent) return "失败比例上限需在 0–100%"
    if (!speed.minDownloadMiBps.isFinite() || speed.minDownloadMiBps !in SettingsLimits.minSpeedMiBps) {
        return "最低下载速度需在 0–10000 MiB/s"
    }
    if (!speed.minUploadMiBps.isFinite() || speed.minUploadMiBps !in SettingsLimits.minSpeedMiBps) {
        return "最低上传速度需在 0–10000 MiB/s"
    }
    if (speed.stopAfter !in SettingsLimits.stopAfter) return "自动停止航班数不能为负数"

    val media = settings.media
    if (media.platforms.size > SettingsLimits.MAX_PLATFORMS) return "流媒体平台数量过多"
    if (media.platforms.any { it.isBlank() }) return "流媒体平台标识不能为空"
    if (media.enabled && media.platforms.isEmpty()) return "启用流媒体检测时至少选择一个平台"
    if (media.concurrency !in SettingsLimits.mediaConcurrency) {
        return "流媒体并发需在 ${SettingsLimits.mediaConcurrency.rangeText()}"
    }
    if (media.timeoutSeconds !in SettingsLimits.mediaTimeoutSeconds) {
        return "流媒体超时需在 ${SettingsLimits.mediaTimeoutSeconds.rangeText()} 秒"
    }
    if (media.retries !in SettingsLimits.mediaRetries) {
        return "流媒体重试次数需在 ${SettingsLimits.mediaRetries.rangeText()}"
    }

    val subscription = settings.subscription
    if (subscription.timeoutSeconds !in SettingsLimits.subscriptionTimeoutSeconds) {
        return "塔台超时需在 ${SettingsLimits.subscriptionTimeoutSeconds.rangeText()} 秒"
    }
    if (subscription.retries !in SettingsLimits.subscriptionRetries) {
        return "塔台重试次数需在 ${SettingsLimits.subscriptionRetries.rangeText()}"
    }
    if (subscription.maxMiB !in SettingsLimits.subscriptionMaxMiB) {
        return "塔台大小上限需在 ${SettingsLimits.subscriptionMaxMiB.rangeText()} MiB"
    }
    if (!SettingsLimits.userAgentOr(subscription.userAgent).equals(subscription.userAgent)) {
        return "塔台 User-Agent 过长或包含控制字符"
    }
    if (subscription.includePattern.isNotEmpty() && !SettingsLimits.isValidRegex(subscription.includePattern)) {
        return "包含规则不是有效的正则表达式"
    }
    if (subscription.excludePattern.isNotEmpty() && !SettingsLimits.isValidRegex(subscription.excludePattern)) {
        return "排除规则不是有效的正则表达式"
    }
    if (subscription.includePattern.length > SettingsLimits.MAX_PATTERN_LENGTH ||
        subscription.excludePattern.length > SettingsLimits.MAX_PATTERN_LENGTH
    ) {
        return "塔台过滤规则过长"
    }
    if (subscription.protocols.size > SettingsLimits.MAX_PROTOCOLS) return "协议数量过多"
    if (subscription.protocols.any { it.isBlank() }) return "协议标识不能为空"

    if (settings.mirrors.size > MirrorCodec.MAX_MIRRORS) return "镜像数量不能超过 ${MirrorCodec.MAX_MIRRORS} 个"
    val normalizedIds = settings.mirrors.map { it.id.trim() }
    if (normalizedIds.any { !SettingsLimits.isUsableMirrorId(it) }) return "镜像标识不能为空、不能包含空白或控制字符，且不能使用 direct"
    if (normalizedIds.map { it.lowercase() }.toSet().size != normalizedIds.size) return "镜像标识不能重复"
    settings.mirrors.forEach { entry ->
        validateMirror(entry)?.let { return it }
    }
    val addresses = settings.mirrors.map(::normalizedMirrorAddress)
    if (addresses.toSet().size != addresses.size) return "镜像地址不能重复"
    val selected = settings.selectedMirrorId
    if (selected != null && settings.mirrors.none { it.id == selected }) {
        return "选中的镜像不存在"
    }
    return null
}

private fun IntRange.rangeText(): String =
    if (first == last) "$first" else "$first–$last"
