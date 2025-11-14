package com.loopers.domain.product.signal

interface ProductTotalSignalRepository {
    fun findByProductId(productId: Long): ProductTotalSignalModel?
}
