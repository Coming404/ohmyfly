package dev.ohmydrift.settings

/**
 * 同一路径只允许一个活动 DataStore，重复创建会被 DataStore 拒绝连接。
 * 仅在 App/UI 主线程调用（remember 与平台入口），因此使用普通 Map 而不加锁；
 * 若将来从后台线程调用，需改为线程安全实现。
 */
internal class RepositoryCache<T> {
    private val entries = mutableMapOf<String, T>()

    fun getOrPut(path: String, create: () -> T): T = entries.getOrPut(path, create)
}
