package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderCriteria
import com.loopers.support.values.Money
import io.swagger.v3.oas.annotations.media.Schema

class OrderV1Request {
    @Schema(description = "주문 생성 요청")
    data class PlaceOrder(
        @field:Schema(
            description = "주문 상품 목록",
        )
        val items: List<PlaceOrderItem>,
        @field:Schema(
            description = "사용할 포인트 (선택, 쿠폰 할인 후 최종 결제 금액과 일치해야 함)",
        )
        val usePoint: Int? = null,
        @field:Schema(
            description = "사용할 발급된 쿠폰 ID (선택)",
        )
        val issuedCouponId: Long? = null,
    ) {
        fun toCriteria(userId: Long): OrderCriteria.PlaceOrder {
            return OrderCriteria.PlaceOrder(
                userId = userId,
                items = items.map { it.toCriteria() },
                usePoint = usePoint?.let { Money.krw(it) } ?: Money.ZERO_KRW,
                issuedCouponId = issuedCouponId,
            )
        }
    }

    @Schema(description = "주문 상품 항목")
    data class PlaceOrderItem(
        @field:Schema(
            description = "상품 ID",
        )
        val productId: Long,
        @field:Schema(
            description = "주문 수량 (양수)",
        )
        val quantity: Int,
    ) {
        fun toCriteria(): OrderCriteria.PlaceOrderItem {
            return OrderCriteria.PlaceOrderItem(
                productId = productId,
                quantity = quantity,
            )
        }
    }
}
