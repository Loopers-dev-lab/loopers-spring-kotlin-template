package com.loopers.domain.like

import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class ProductLikeService(
    private val productLikeRepository: ProductLikeRepository,
) {
    fun getAllBy(productIds: List<Long>): List<ProductLike> = productLikeRepository.findAllBy(productIds)

    fun getAllBy(productId: Long): List<ProductLike> = productLikeRepository.findAllBy(productId)

    fun getAllBy(userId: Long, pageable: Pageable): Page<ProductLike> = productLikeRepository.findAllBy(userId, pageable)

    fun like(product: Product, user: User) {
        productLikeRepository.findBy(product.id, user.id) ?: productLikeRepository.save(ProductLike.create(product.id, user.id))
    }

    fun unlike(product: Product, user: User) {
        productLikeRepository.findBy(product.id, user.id) ?: return
        return productLikeRepository.deleteBy(product.id, user.id)
    }
}
