package com.loopers.interfaces.api.order

import io.swagger.v3.oas.annotations.media.Schema

sealed class OrderRequest {

    data class CreateOrderDto(
        @get:Schema(description = "주문 상품 목록", example = "[{\"productId\":1,\"quantity\":2}]")
        val items: List<OrderItemDto>,
    ) {
        data class OrderItemDto(
            @get:Schema(description = "상품 ID", example = "1")
            val productId: Long,

            @get:Schema(description = "주문 수량", example = "2")
            val quantity: Int,
        )
    }
}
