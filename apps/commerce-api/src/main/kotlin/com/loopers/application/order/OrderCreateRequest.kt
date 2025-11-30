package com.loopers.application.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class OrderCreateRequest(
    val items: List<OrderItemRequest>,
    val couponId: Long? = null,
    // POINT or CARD
    val paymentMethod: String = "POINT",
    val cardType: String? = null,
    val cardNo: String? = null,
)

data class OrderItemRequest(
    val productId: Long,
    val quantity: Int,
) {
    init {
        if (quantity <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 0보다 커야 합니다. 현재 값: $quantity")
        }
    }
}
