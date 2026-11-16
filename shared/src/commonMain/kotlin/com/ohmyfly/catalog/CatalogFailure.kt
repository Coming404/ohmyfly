// 目录操作在失败边界统一提供可读原因；日志不记录订阅正文、地址路径或桥接载荷。
package com.ohmyfly.catalog

import com.ohmyfly.logging.logger
import com.ohmyfly.nativecore.NativeCoreException
import kotlinx.serialization.SerializationException

// 序列化错误可能包含整份 JSON，仅保留字段路径；其他原因删除 URL 与敏感赋值。
internal fun catalogFailure(error: Exception): String {
    if (error is SerializationException) {
        val field = Regex("path: (\\$[A-Za-z0-9_.\\[\\]]+)").find(error.message.orEmpty())?.groupValues?.get(1)
        return "塔台与航班数据格式不匹配" + (field?.let { "（$it）" } ?: "")
    }
    val reason = error.message.orEmpty().substringBefore("JSON input:").lineSequence().firstOrNull().orEmpty()
        .replace(Regex("[A-Za-z][A-Za-z0-9+.-]*://\\S+"), "[地址已隐藏]")
        .replace(Regex("(?i)(password|token|secret|authorization|uuid|private[-_]?key)\\s*[:=]\\s*\\S+"), "$1=[已隐藏]")
        .take(240)
    return when {
        error is NativeCoreException -> "塔台与航班数据操作失败：${reason.ifBlank { "本地核心返回错误" }}"
        reason.isNotBlank() -> reason
        else -> "操作失败（${error::class.simpleName ?: "未知错误"}）"
    }
}

// 记录阶段、来源 ID 与异常类型，使导入前失败也能在日志中定位。
internal fun logCatalogFailure(stage: String, error: Exception): String {
    val reason = catalogFailure(error)
    logger.info { "[塔台航班] ${stage}失败：$reason" }
    return reason
}
