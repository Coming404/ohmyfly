// HEAD 仅返回状态与响应头耗时，不承载正文。
package com.ohmyfly.mirror

internal data class MirrorHeadResponse(
    val statusCode: Int,
    val latencyMs: Long,
)
