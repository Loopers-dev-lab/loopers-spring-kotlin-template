package com.loopers.domain.ranking.event

/**
 * RankingOrderPaidEventV1 - order.paid.v1 이벤트의 Ranking Consumer Contract
 *
 * - 발행자: commerce-api (Order 도메인)
 * - totalAmount를 orderItems 수로 균등 분배하여 상품별 점수 계산에 사용
 */
data class RankingOrderPaidEventV1(
    val totalAmount: Long,
    val orderItems: List<OrderItem>,
) {
    data class OrderItem(
        val productId: Long,
    )
}
