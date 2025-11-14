package com.loopers.interfaces.api.order

import com.loopers.domain.order.Order
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

sealed class OrderResponse {

    data class OrderInfoDto(
        @get:Schema(description = "주문 ID", example = "1")
        val orderId: Long,

        @get:Schema(description = "사용자 ID", example = "1")
        val userId: Long,

        @get:Schema(description = "총 금액", example = "50000.00")
        val totalAmount: BigDecimal,

        @get:Schema(description = "주문 상태", example = "COMPLETED")
        val status: String,
    ) {
        companion object {
            fun from(order: Order): OrderInfoDto {
                return OrderInfoDto(
                    orderId = order.id,
                    userId = order.userId,
                    totalAmount = order.totalAmount,
                    status = order.status.name,
                )
            }
        }
    }
}
