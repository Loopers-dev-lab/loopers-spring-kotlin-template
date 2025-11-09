package com.loopers.application.order

data class OrderCreateRequest(
    val items: List<OrderItemRequest>,
)

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int,
) {
    init {
        require(quantity > 0) { "주문 수량은 0보다 커야 합니다. 현재 값: $quantity" }
    }
}
