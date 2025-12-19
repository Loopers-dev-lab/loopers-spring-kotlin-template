package com.loopers.domain.product

/**
 * 재고 소진 상품 캐시 무효화 커맨드
 */
data class EvictStockDepletedCommand(
    val productIds: List<Long>,
)
