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

    @Transactional(readOnly = true)
    override fun findAllByProductIds(productIds: List<Long>): List<ProductStatistic> {
        if (productIds.isEmpty()) return emptyList()
        return productStatisticJpaRepository.findAllByProductIdIn(productIds)
    }

    @Transactional
    override fun increaseLikeCountBy(productId: Long) {
        return productStatisticJpaRepository.incrementLikeCount(productId)
    }

    @Transactional
    override fun decreaseLikeCountBy(productId: Long) {
        return productStatisticJpaRepository.decrementLikeCount(productId)
    }

    @Transactional
    override fun increaseSalesCountBy(productId: Long, amount: Int) {
        productStatisticJpaRepository.incrementSalesCount(productId, amount)
    }

    @Transactional
    override fun increaseViewCountBy(productId: Long) {
        productStatisticJpaRepository.incrementViewCount(productId)
    }

    @Transactional
    override fun save(productStatistic: ProductStatistic): ProductStatistic {
        return productStatisticJpaRepository.save(productStatistic)
    }
}
