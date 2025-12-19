package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ProductStatisticService - 상품 통계 집계 비즈니스 로직
 *
 * - 이벤트 처리에 따른 메트릭 집계
 * - Repository 레벨에서 트랜잭션 처리
 */
@Service
class ProductStatisticService(
    private val productStatisticRepository: ProductStatisticRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun increaseLikeCount(productId: Long) {
        productStatisticRepository.incrementLikeCount(productId)
        log.debug("Increased like count for productId={}", productId)
    }

    fun decreaseLikeCount(productId: Long) {
        productStatisticRepository.decrementLikeCount(productId)
        log.debug("Decreased like count for productId={}", productId)
    }

    fun increaseSalesCount(orderItems: List<OrderItemSnapshot>) {
        orderItems.forEach { item ->
            productStatisticRepository.incrementSalesCount(item.productId, item.quantity)
            log.debug("Increased sales count for productId={}, amount={}", item.productId, item.quantity)
        }
    }

    fun increaseViewCount(productId: Long) {
        productStatisticRepository.incrementViewCount(productId)
        log.debug("Increased view count for productId={}", productId)
    }
}
