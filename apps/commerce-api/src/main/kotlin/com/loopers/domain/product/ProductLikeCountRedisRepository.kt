package com.loopers.domain.product

interface ProductLikeCountRedisRepository {
    fun incrementIfExists(productId: Long): Long?
    fun initAndIncrement(productId: Long, initialValue: Long): Long
    fun decrementIfPositive(productId: Long): Long?
    fun initAndDecrementIfPositive(productId: Long, initialValue: Long): Long
    fun get(productId: Long): Long?
    fun setIfAbsent(productId: Long, value: Long): Boolean
    fun getAfterSetIfAbsent(productId: Long): Long?
    fun getAllKeys(): Set<String>?
    fun extractProductId(key: String): Long?

    companion object {
        const val KEY_NOT_FOUND = -1L
    }
}
