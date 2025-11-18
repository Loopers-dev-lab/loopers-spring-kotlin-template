package com.loopers.domain.product

interface ProductStatisticRepository {
    fun findByProductId(productId: Long): ProductStatistic?
    fun findAllByProductIds(productIds: List<Long>): List<ProductStatistic>
    fun increaseLikeCountBy(productId: Long)
    fun decreaseLikeCountBy(productId: Long)
    fun save(productStatistic: ProductStatistic): ProductStatistic
}
