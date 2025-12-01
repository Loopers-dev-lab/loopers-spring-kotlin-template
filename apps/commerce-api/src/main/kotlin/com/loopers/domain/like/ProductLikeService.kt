package com.loopers.domain.like

import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductLikeService(
    private val productLikeRepository: ProductLikeRepository,
) {
    @Transactional(readOnly = true)
    fun getCountAllBy(productIds: List<Long>): List<ProductLikeCount> = productLikeRepository.findCountAllBy(productIds)

    @Transactional(readOnly = true)
    fun getCountBy(productId: Long): ProductLikeCount = productLikeRepository.findCountBy(productId)

    @Transactional(readOnly = true)
    fun getBy(productId: Long, userId: Long): ProductLike? = productLikeRepository.findBy(productId, userId)

    @Transactional(readOnly = true)
    fun getAllBy(userId: Long, pageable: Pageable): Page<ProductLike> = productLikeRepository.findAllBy(userId, pageable)

    @Transactional
    fun like(product: Product, user: User) {
        if (productLikeRepository.existsBy(product.id, user.id)) {
            return
        }
        val result = productLikeRepository.increaseCount(product.id)
        if (result == 0) {
            productLikeRepository.saveCount(ProductLikeCount.create(product.id, 1L))
        }
        productLikeRepository.save(ProductLike.create(product.id, user.id))
    }

    @Transactional
    fun unlike(product: Product, user: User) {
        productLikeRepository.findBy(product.id, user.id) ?: return
        productLikeRepository.decreaseCount(product.id)
        return productLikeRepository.deleteBy(product.id, user.id)
    }
}
