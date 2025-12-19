package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProductStatisticRdbRepository(
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
) : ProductStatisticRepository {

    @Transactional(readOnly = true)
    override fun findByProductId(productId: Long): ProductStatistic? {
        return productStatisticJpaRepository.findByProductId(productId)
    }

    @Transactional
    override fun incrementLikeCount(productId: Long) {
        productStatisticJpaRepository.incrementLikeCount(productId)
    }

    @Transactional
    override fun decrementLikeCount(productId: Long) {
        productStatisticJpaRepository.decrementLikeCount(productId)
    }

    @Transactional
    override fun incrementSalesCount(productId: Long, amount: Int) {
        productStatisticJpaRepository.incrementSalesCount(productId, amount)
    }

    @Transactional
    override fun incrementViewCount(productId: Long) {
        productStatisticJpaRepository.incrementViewCount(productId)
    }
}
