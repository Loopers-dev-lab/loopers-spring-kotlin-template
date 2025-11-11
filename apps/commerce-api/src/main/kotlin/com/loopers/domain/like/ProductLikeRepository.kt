package com.loopers.domain.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductLikeRepository {
    fun findAllBy(productIds: List<Long>): List<ProductLike>
    fun findAllBy(productId: Long): List<ProductLike>
    fun findAllBy(userId: Long, pageable: Pageable): Page<ProductLike>
    fun findBy(productId: Long, userId: Long): ProductLike?
    fun save(productLike: ProductLike): ProductLike
    fun deleteBy(productId: Long, userId: Long)
}
