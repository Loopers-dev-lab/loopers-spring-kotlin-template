package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderItemCommand
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.config.AuthUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {

    @PostMapping
    override fun createOrder(
        authUser: AuthUser,
        @RequestBody request: OrderRequest.CreateOrderDto,
    ): ApiResponse<OrderResponse.OrderInfoDto> {
        val order = orderFacade.createOrder(
            userId = authUser.userId,
            items = request.items.map { item ->
                OrderItemCommand(
                    productId = item.productId,
                    quantity = item.quantity,
                )
            },
        )
        return ApiResponse.success(OrderResponse.OrderInfoDto.from(order))
    }
}
