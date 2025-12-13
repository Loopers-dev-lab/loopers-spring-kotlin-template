package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductMetrics
import com.loopers.domain.product.ProductMetricsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductMetricsRepositoryImpl(
    private val jpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {

    override fun save(productMetrics: ProductMetrics): ProductMetrics {
        return jpaRepository.save(productMetrics)
    }

    override fun findByProductId(productId: Long): ProductMetrics? {
        return jpaRepository.findByProductId(productId)
    }

    override fun findOrCreateByProductId(productId: Long): ProductMetrics {
        return findByProductId(productId) ?: ProductMetrics.create(productId)
    }
}
