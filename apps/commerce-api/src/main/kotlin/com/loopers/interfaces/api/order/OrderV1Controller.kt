package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.annotation.Authenticated
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {
    @Authenticated
    @PostMapping("")
    override fun requestOrder(
        @Valid @RequestBody request: OrderV1Dto.OrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        return orderFacade.requestOrder(request.toRequestOrder())
            .let { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
