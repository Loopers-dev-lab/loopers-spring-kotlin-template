package com.loopers.support.cache

class CacheKeyDsl(private val namespace: String) {
    private val parts = mutableListOf<String>()
    fun part(k: String, v: Any?) = apply {
        val sv = when (v) {
            null -> "all"
            is Collection<*> ->
                v
                .map { it.toString() }
                .sorted()
                .joinToString(",")
            else -> v.toString()
        }
        parts += "$k:$sv"
    }
    fun build() = "$namespace:${parts.joinToString(":")}"
}
