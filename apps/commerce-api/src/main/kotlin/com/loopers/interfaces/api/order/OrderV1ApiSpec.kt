package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.config.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping

@Tag(name = "주문 API", description = "주문 관련 API")
interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "여러 상품을 포함한 주문을 생성하고 결제를 수행합니다.")
    @PostMapping
    fun createOrder(
        authUser: AuthUser,
        request: OrderRequest.CreateOrderDto,
    ): ApiResponse<OrderResponse.OrderInfoDto>
}
