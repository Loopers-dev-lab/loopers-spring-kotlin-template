package com.loopers.interfaces.api.v1.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun placeOrder(
        @RequestHeader(value = "X-USER-ID") userId: String,
        @RequestBody request: OrderV1Dto.CreateOrderRequest,
    ): ApiResponse<OrderV1Dto.CreateOrderResponse> {
        orderFacade.placeOrder(request.toCommand(userId))
        return ApiResponse.success(OrderV1Dto.CreateOrderResponse())
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader(value = "X-USER-ID") userId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>> {
        val orderPage = orderFacade.getOrders(userId, pageable)

        return PageResponse.from(
            content = OrderV1Dto.OrderListResponse.from(orderPage.content),
            page = orderPage,
        ).let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @RequestHeader(value = "X-USER-ID") userId: String,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse> = orderFacade.getOrder(userId, orderId)
        .let { OrderV1Dto.OrderDetailResponse.from(it) }
        .let { ApiResponse.success(it) }
}
