package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) {

    @PostMapping
    fun placeOrder(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody request: OrderV1Request.PlaceOrder,
    ): ApiResponse<OrderV1Response.PlaceOrder> {
        return orderFacade.placeOrder(request.toCriteria(userId))
            .let { OrderV1Response.PlaceOrder.from(it) }
            .let { ApiResponse.success(it) }
    }
}
