package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val orderFacade: OrderFacade) : OrderApiSpec {

    @PostMapping
    override fun createOrder(
        @RequestHeader("X-USER-ID") userId: Long,
        @Valid @RequestBody request: OrderDto.Request,
    ): ApiResponse<OrderDto.Response> {
        val command = request.toCommand()

        return orderFacade.order(userId, command)
            .let { OrderDto.Response.from(it) }
            .let { ApiResponse.success(it) }
    }
}
