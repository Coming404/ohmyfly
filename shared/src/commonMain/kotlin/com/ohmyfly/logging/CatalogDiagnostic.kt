// 网络和桥接失败保留异常链与字段路径，订阅凭据和载荷仍在日志边界脱敏。
package com.ohmyfly.logging

import kotlinx.serialization.SerializationException

/** 保留可定位的诊断文字，删除地址、敏感赋值及序列化载荷。 */
internal fun catalogDiagnostic(value: String): String = value.substringBefore("JSON input:")
    .replace(Regex("[A-Za-z][A-Za-z0-9+.-]*://[^\\s\"<>]+"), "[地址已隐藏]")
    .replace(Regex("(?i)(password|passwd|token|secret|authorization|uuid|private[-_]?key)\\s*[:=]\\s*[^\\s,;]+"), "$1=[已隐藏]")
    .replace(Regex("[\\r\\n\\t]+"), " ")
    .take(1600)

/** 序列化错误可能回显节点配置，仅记录异常类型和 JSON 字段路径。 */
internal fun catalogExceptionDetails(error: Throwable): String = generateSequence(error) { it.cause }.take(6)
    .joinToString(" <- ") { cause ->
        val detail = if (cause is SerializationException) {
            Regex("path: (\\$[A-Za-z0-9_.\\[\\]]+)").find(cause.message.orEmpty())?.groupValues?.get(1)
                ?.let { "字段 $it 不符合数据契约" } ?: "数据结构不符合契约"
        } else catalogDiagnostic(cause.message.orEmpty())
        "${cause::class.simpleName}: $detail"
    }
