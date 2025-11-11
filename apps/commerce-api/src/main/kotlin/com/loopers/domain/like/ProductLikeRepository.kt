package com.loopers.domain.like

interface ProductLikeRepository {
    fun findAllBy(productIds: List<Long>): List<ProductLike>
}
