package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductMetrics
import com.loopers.domain.product.ProductMetricsRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProductMetricsRepositoryImpl(private val jpaRepository: ProductMetricsJpaRepository) : ProductMetricsRepository {

    override fun save(productMetrics: ProductMetrics): ProductMetrics = jpaRepository.save(productMetrics)

    override fun findByProductId(productId: Long): ProductMetrics? = jpaRepository.findByProductId(productId)

    override fun findOrCreateByProductId(productId: Long): ProductMetrics = findByProductId(productId) ?: run {
        jpaRepository.save(ProductMetrics.create(productId))
    }

    @Transactional
    override fun findOrCreateByProductIdWithLock(productId: Long): ProductMetrics {
        // 비관적 락으로 조회 (DB 레벨 행 잠금)
        val existing = jpaRepository.findByProductIdWithLock(productId)
        if (existing != null) {
            return existing
        }

        // 존재하지 않으면 생성 후 저장 (동일 트랜잭션 내에서)
        return jpaRepository.save(ProductMetrics.create(productId))
    }

    override fun findAllByProductIdIn(productIds: List<Long>): List<ProductMetrics> =
        jpaRepository.findAllByProductIdIn(productIds)
}
