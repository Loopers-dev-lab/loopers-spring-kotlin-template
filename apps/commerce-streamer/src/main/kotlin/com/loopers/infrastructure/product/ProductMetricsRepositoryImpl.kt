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

    override fun findOrCreateByProductIdWithLock(productId: Long): ProductMetrics {
        // 비관적 락으로 조회
        val existing = jpaRepository.findByProductIdWithLock(productId)
        if (existing != null) {
            return existing
        }

        // 존재하지 않으면 생성 후 저장 (동일 트랜잭션 내에서)
        val newMetrics = ProductMetrics.create(productId)
        return jpaRepository.save(newMetrics)
    }
}
