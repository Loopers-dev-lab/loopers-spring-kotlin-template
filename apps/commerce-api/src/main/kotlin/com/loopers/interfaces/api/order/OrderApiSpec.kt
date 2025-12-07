package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Order", description = "주문 API")
interface OrderApiSpec {

    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    fun createOrder(userId: Long, request: OrderDto.Request): ApiResponse<OrderDto.Response>
}
