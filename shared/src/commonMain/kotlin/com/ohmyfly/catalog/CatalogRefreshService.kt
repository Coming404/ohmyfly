package com.ohmyfly.catalog

import com.ohmyfly.settings.SubscriptionSettings
import com.ohmyfly.subscription.*
import io.ktor.http.parseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal typealias CatalogRequest = (String, JsonObjectBuilder.() -> Unit) -> JsonObject

// 同一刷新契约由真实目录与既有仓储行为测试使用，事实只写入 repository。
internal class CatalogRefreshService(
    private val importer: SubscriptionImporter,
    private val request: CatalogRequest = ::catalogCall,
    private val committing: () -> Unit = {},
) {
    suspend fun add(address: String, settings: SubscriptionSettings, name: String = "",
        mirrors: SubscriptionMirrorSettings = SubscriptionMirrorSettings()): String? {
        validateSubscriptionUrl(address)?.let { return it }
        val id = request("add") { put("address", address.trim()); put("name", name.takeUnless { it.isBlank() } ?: "") }.getValue("id").jsonPrimitive.long
        refresh(id, settings, mirrors)
        return null
    }

    private fun commit(ticket: RefreshTicket, block: JsonObjectBuilder.() -> Unit) {
        try { request("commit", block) } catch (error: Exception) {
            val status = request("status") { put("id", ticket.id) }
            if (status.getValue("committed").jsonPrimitive.long == ticket.generation) return
            if (!status.getValue("exists").jsonPrimitive.boolean ||
                status.getValue("generation").jsonPrimitive.long != ticket.generation) throw CatalogStaleException()
            throw error
        }
    }

    suspend fun importBody(body: SubscriptionBody, settings: SubscriptionSettings, replaceId: Long? = null) {
        validateSubscriptionBody(body.content, settings.maxMiB)
        val id = replaceId ?: request("add") {
            put("address", newBodyAddress(body.kind)); put("name", body.name)
        }.getValue("id").jsonPrimitive.long
        val ticket = catalogJson.decodeFromJsonElement<RefreshTicket>(request("begin") { put("id", id) })
        val writer = CatalogRecordWriter(ticket, request)
        try {
            require(ticket.address.startsWith("local:")) { "只能重新导入本地塔台" }
            val result = importer.importBody(body.content, settings, writer::append)
            result.failure?.let { failure ->
                request("fail") { put("id", id); put("generation", ticket.generation); put("failure", failure); put("name", body.name) }
                throw IllegalArgumentException(failure)
            }
            committing()
            writer.flush()
            currentCoroutineContext().ensureActive()
            commit(ticket) {
                put("refresh", buildJsonObject {
                    put("id", id); put("generation", ticket.generation); put("count", result.recordCount)
                    put("name", body.name); put("usage", JsonNull)
                    put("warnings", JsonArray(result.warnings.map(::JsonPrimitive)))
                })
            }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                try { request("abort") { put("id", id); put("generation", ticket.generation) } }
                catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }

    suspend fun refresh(id: Long, settings: SubscriptionSettings, mirrors: SubscriptionMirrorSettings): Boolean {
        val ticket = catalogJson.decodeFromJsonElement<RefreshTicket>(request("begin") { put("id", id) })
        val effective = settings
        val writer = CatalogRecordWriter(ticket, request)
        try {
            require(!ticket.address.startsWith("local:")) { "本地塔台请重新选择文件或粘贴正文" }
            val result = importer.importSubscription(ticket.address, effective, ticket.name.isBlank(), mirrors, writer::append)
            if (result.failure != null) {
                logCatalogFailure("来源 $id 网络导入", IllegalArgumentException(result.failure))
                request("fail") { put("id", id); put("generation", ticket.generation)
                    put("failure", result.failure); put("name", parseUrl(ticket.address)!!.host) }
                return false
            }
            committing()
            writer.flush()
            currentCoroutineContext().ensureActive()
            commit(ticket) {
                put("refresh", buildJsonObject {
                    put("id", id); put("generation", ticket.generation); put("count", result.recordCount)
                    put("name", result.name ?: parseUrl(ticket.address)!!.host)
                    put("usage", result.usage?.let { catalogJson.encodeToJsonElement(SubscriptionUsage.serializer(), it) } ?: JsonNull)
                    put("warnings", JsonArray(result.warnings.map(::JsonPrimitive)))
                })
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                try { request("abort") { put("id", id); put("generation", ticket.generation) } }
                catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            }
            throw e
        } catch (_: CatalogStaleException) {
            // 删除或新刷新代际已先完成，旧结果不再写入。
        } catch (error: Exception) {
            request("abort") { put("id", id); put("generation", ticket.generation) }
            throw error
        }
        return true
    }
}
