package com.loopers.domain.product

sealed interface CacheResult<out T> {
    data class Hit<T>(val value: T) : CacheResult<T>
    data object Miss : CacheResult<Nothing>
    data class Error(val exception: Exception) : CacheResult<Nothing>
}
