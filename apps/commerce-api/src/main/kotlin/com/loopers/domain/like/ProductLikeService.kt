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
    fun getAllBy(productIds: List<Long>): List<ProductLike> = productLikeRepository.findAllBy(productIds)

    @Transactional(readOnly = true)
    fun getAllBy(productId: Long): List<ProductLike> = productLikeRepository.findAllBy(productId)

    @Transactional(readOnly = true)
    fun getAllBy(userId: Long, pageable: Pageable): Page<ProductLike> = productLikeRepository.findAllBy(userId, pageable)

    @Transactional
    fun like(product: Product, user: User) {
        if (productLikeRepository.existsBy(product.id, user.id)) {
            return
        }
        productLikeRepository.save(ProductLike.create(product.id, user.id))
    }

    @Transactional
    fun unlike(product: Product, user: User) {
        productLikeRepository.findBy(product.id, user.id) ?: return
        return productLikeRepository.deleteBy(product.id, user.id)
    }
}
