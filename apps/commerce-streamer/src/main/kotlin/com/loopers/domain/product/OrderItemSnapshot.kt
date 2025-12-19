package com.loopers.domain.product

/**
 * 주문 아이템 스냅샷 DTO
 *
 * - 판매량 집계를 위한 주문 아이템 정보
 * - productId와 quantity만 포함
 */
data class OrderItemSnapshot(
    val productId: Long,
    val quantity: Int,
)
