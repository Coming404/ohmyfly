// UA 保留可读标识，敏感赋值脱敏；受控失败与跳过原因翻译为中文。
package com.ohmyfly.subscription

import com.ohmyfly.logging.catalogDiagnostic

internal fun subscriptionUserAgentDescription(value: String?): String = when (value) {
    null -> "不设置 User-Agent 请求头"
    MozillaSubscriptionUserAgent -> "浏览器 UA「$value」"
    in BuiltInSubscriptionUserAgents -> "内置 UA「$value」"
    else -> "自定义 UA「${catalogDiagnostic(value)}」"
}

internal fun subscriptionFailureDescription(reason: String): String = when (reason) {
    "订阅请求失败" -> "塔台请求失败：网络连接中断，请检查网络后重试"
    "订阅解析失败" -> "塔台内容解析失败：格式无法识别或节点字段无效"
    "HTTP 401" -> "服务器要求身份验证（HTTP 401）"
    "HTTP 403" -> "服务器拒绝访问（HTTP 403）"
    "HTTP 404" -> "服务器未找到订阅资源（HTTP 404）"
    "HTTP 429" -> "请求过于频繁，服务器限制访问（HTTP 429）"
    "HTTP 500" -> "服务器内部错误（HTTP 500）"
    "HTTP 502" -> "服务器网关收到无效响应（HTTP 502）"
    "HTTP 503" -> "服务器暂时无法提供服务（HTTP 503）"
    "HTTP 504" -> "服务器网关等待上游响应超时（HTTP 504）"
    else -> reason
}

// 网络异常按用户能采取行动的原因分类，不将凭据或底层异常链放入通知。
internal fun subscriptionRequestFailure(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(6).toList()
    val response = causes.filterIsInstance<io.ktor.client.plugins.ResponseException>().firstOrNull()
    if (response != null) return "塔台请求失败：${subscriptionFailureDescription("HTTP ${response.response.status.value}")}"
    val detail = causes.joinToString(" ") { "${it::class.simpleName} ${it.message}" }.lowercase()
    val reason = when {
        "timeout" in detail || "timed out" in detail -> "服务器响应超时"
        "unknownhost" in detail || "resolve" in detail || "name resolution" in detail -> "无法解析服务器地址"
        "certificate" in detail || "ssl" in detail || "tls" in detail -> "安全连接建立失败，请检查服务器证书"
        "refused" in detail -> "服务器拒绝连接"
        "offline" in detail || "not connected" in detail -> "当前网络不可用"
        else -> "网络连接中断，请检查网络后重试"
    }
    return "塔台请求失败：$reason"
}

internal fun subscriptionSkipDescription(reason: String): String = when (reason) {
    "malformed link" -> "链接格式不完整，缺少合法的协议前缀和 :// 分隔符"
    "invalid base64" -> "Base64 内容无法解码"
    "base64 depth exceeded" -> "Base64 编码超过最多 3 层的解码限制"
    "unknown format" -> "无法识别订阅内容格式"
    "empty YAML" -> "YAML 内容为空"
    "unrecognized YAML" -> "YAML 根结构无法识别"
    "malformed YAML item" -> "YAML 节点条目结构损坏"
    "invalid YAML item" -> "YAML 节点字段无效，无法规范化为节点"
    "malformed proxy-provider" -> "子订阅引用的 YAML 结构损坏"
    "malformed YAML flow collection" -> "YAML 行内集合的括号或结构不完整"
    "yaml tab indentation" -> "YAML 使用了不支持的制表符缩进"
    "yaml ambiguous list indentation" -> "YAML 列表缩进存在歧义"
    "yaml inconsistent list indentation" -> "YAML 列表缩进不一致"
    "yaml inconsistent provider indentation" -> "子订阅引用的 YAML 缩进不一致"
    "yaml item too large" -> "单个 YAML 条目超过大小限制"
    "yaml cross-item alias unsupported" -> "YAML 逐项恢复不支持跨条目的别名引用"
    "yaml duplicate root key: proxies" -> "YAML 重复定义了节点列表 proxies"
    "yaml duplicate root key: proxy-providers" -> "YAML 重复定义了子订阅列表 proxy-providers"
    "line too long" -> "输入行超过长度限制"
    "read error" -> "读取订阅内容失败"
    else -> {
        // 只接受 Go 生成的协议原因格式，不把未知原因原文带入日志。
        val unsupported = Regex("unsupported protocol: ([a-z][a-z0-9+.-]*)").matchEntire(reason)
        val invalid = Regex("invalid ([a-z][a-z0-9+.-]*) link").matchEntire(reason)
        when {
            unsupported != null -> "不支持 ${unsupported.groupValues[1]} 协议"
            invalid != null -> "${invalid.groupValues[1]} 链接字段无效，无法解析或规范化为节点"
            else -> "解析器返回了未识别的跳过原因"
        }
    }
}
