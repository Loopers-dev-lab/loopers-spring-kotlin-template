package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * ProductStatisticService - 상품 통계 배치 집계 비즈니스 로직
 *
 * - Command 기반 배치 처리로 N+1 쿼리 방지
 * - 트랜잭션 단위로 일괄 조회, 계산, 저장
 * - Sales Count는 낙관적 락 재시도로 동시성 제어
 */
@Service
class ProductStatisticService(
    private val productStatisticRepository: ProductStatisticRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

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

    /**
     * Sales Count 업데이트 - 낙관적 락 재시도 적용
     *
     * Order 이벤트는 orderId 기반 파티셔닝이라 동일 productId에 대한
     * 동시 업데이트가 발생할 수 있어 재시도 로직으로 동시성 제어
     */
    fun updateSalesCount(command: UpdateSalesCountCommand) {
        if (command.items.isEmpty()) return

        executeWithRetry {
            doUpdateSalesCount(command)
        }
    }

    private fun doUpdateSalesCount(command: UpdateSalesCountCommand) {
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

    private fun executeWithRetry(action: () -> Unit) {
        var lastException: ObjectOptimisticLockingFailureException? = null

        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                transactionTemplate.executeWithoutResult { action() }
                return
            } catch (e: ObjectOptimisticLockingFailureException) {
                lastException = e
                log.warn("Optimistic lock conflict, retry attempt {} of {}", attempt + 1, MAX_RETRY_COUNT)
            }
        }

        log.error("Failed after {} retry attempts", MAX_RETRY_COUNT)
        throw lastException!!
    }
}
