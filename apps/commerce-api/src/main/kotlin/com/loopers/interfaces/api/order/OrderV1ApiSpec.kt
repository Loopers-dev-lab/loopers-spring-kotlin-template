package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "User API 입니다.")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 요청",
        description = "주문을 요청 합니다.",
    )
    fun requestOrder(
        @Schema(name = "주문 요청 정보", description = "주문 요청 정보")
        request: OrderV1Dto.OrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>
}
