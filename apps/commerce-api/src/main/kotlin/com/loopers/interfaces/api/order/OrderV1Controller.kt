package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {

    @PostMapping
    override fun createOrder(
        @RequestHeader("X-USER-ID") memberId: String,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val orderInfo = orderFacade.createOrder(
            memberId = memberId,
            request = request
        )

        return OrderV1Dto.OrderResponse.from(orderInfo)
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader("X-USER-ID") memberId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<Page<OrderV1Dto.OrderResponse>> {
        val pageable = PageRequest.of(page, size)
        val orders = orderFacade.getOrders(memberId, pageable)

        return orders.map { OrderV1Dto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val orderInfo = orderFacade.getOrder(orderId)

        return OrderV1Dto.OrderResponse.from(orderInfo)
            .let { ApiResponse.success(it) }
    }
}

