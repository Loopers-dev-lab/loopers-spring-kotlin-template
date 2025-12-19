package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ProductStatisticService - 상품 통계 배치 집계 비즈니스 로직
 *
 * - Command 기반 배치 처리로 N+1 쿼리 방지
 * - 트랜잭션 단위로 일괄 조회, 계산, 저장
 */
@Service
class ProductStatisticService(
    private val productStatisticRepository: ProductStatisticRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun updateLikeCount(command: UpdateLikeCountCommand) {
        if (command.items.isEmpty()) return

        val productIds = command.items.map { it.productId }.distinct()
        val statistics = productStatisticRepository.findAllByProductIds(productIds)
            .associateBy { it.productId }

        val typesByProductId = command.items.groupBy(
            keySelector = { it.productId },
            valueTransform = { it.type },
        )

        val updatedStatistics = typesByProductId.mapNotNull { (productId, types) ->
            statistics[productId]?.apply { applyLikeChanges(types) }
        }

        productStatisticRepository.saveAll(updatedStatistics)
        log.debug("Updated like count for {} products", updatedStatistics.size)
    }

    @Transactional
    fun updateSalesCount(command: UpdateSalesCountCommand) {
        if (command.items.isEmpty()) return

        val productIds = command.items.map { it.productId }.distinct()
        val statistics = productStatisticRepository.findAllByProductIds(productIds)
            .associateBy { it.productId }

        val quantitiesByProductId = command.items.groupBy(
            keySelector = { it.productId },
            valueTransform = { it.quantity },
        )

        val updatedStatistics = quantitiesByProductId.mapNotNull { (productId, quantities) ->
            statistics[productId]?.apply { applySalesChanges(quantities) }
        }

        productStatisticRepository.saveAll(updatedStatistics)
        log.debug("Updated sales count for {} products", updatedStatistics.size)
    }

    @Transactional
    fun updateViewCount(command: UpdateViewCountCommand) {
        if (command.items.isEmpty()) return

        val productIds = command.items.map { it.productId }.distinct()
        val statistics = productStatisticRepository.findAllByProductIds(productIds)
            .associateBy { it.productId }

        val countByProductId = command.items.groupingBy { it.productId }.eachCount()

        val updatedStatistics = countByProductId.mapNotNull { (productId, count) ->
            statistics[productId]?.apply { applyViewChanges(count) }
        }

        productStatisticRepository.saveAll(updatedStatistics)
        log.debug("Updated view count for {} products", updatedStatistics.size)
    }
}
