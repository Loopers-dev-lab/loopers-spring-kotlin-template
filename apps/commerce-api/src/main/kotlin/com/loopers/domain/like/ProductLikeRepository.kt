package com.loopers.domain.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface
ProductLikeRepository {
    fun findAllBy(productIds: List<Long>): List<ProductLike>
    fun findCountAllBy(productIds: List<Long>): List<ProductLikeCount>
    fun findAllBy(productId: Long): List<ProductLike>
    fun findCountBy(productId: Long): ProductLikeCount
    fun findAllBy(userId: Long, pageable: Pageable): Page<ProductLike>
    fun findBy(productId: Long, userId: Long): ProductLike?
    fun existsBy(productId: Long, userId: Long): Boolean
    fun save(productLike: ProductLike): ProductLike
    fun deleteBy(productId: Long, userId: Long)
    fun saveCount(productLikeCount: ProductLikeCount): ProductLikeCount
    fun increaseCount(productId: Long): Int
    fun decreaseCount(productId: Long): Int
}
