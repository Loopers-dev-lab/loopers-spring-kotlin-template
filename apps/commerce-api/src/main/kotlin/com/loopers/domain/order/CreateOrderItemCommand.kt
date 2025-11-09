package com.loopers.domain.order

data class CreateOrderItemCommand(
    val productId: Long,
    val quantity: Int,
) {
    init {
        require(quantity > 0) { "주문 수량은 0보다 커야 합니다. 현재 값: $quantity" }
    }
}
