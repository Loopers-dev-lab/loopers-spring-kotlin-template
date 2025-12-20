package com.loopers.domain.product

/**
 * 상품 관련 도메인 이벤트
 */
object ProductEvent {

    /**
     * 상품 조회 이벤트
     * 상품 상세 페이지 조회 시 발생
     */
    data class ProductViewed(
        val productId: Long,
        val userId: Long?,
    )

    data class OutOfStock(
        val productId: Long,
    )
}
