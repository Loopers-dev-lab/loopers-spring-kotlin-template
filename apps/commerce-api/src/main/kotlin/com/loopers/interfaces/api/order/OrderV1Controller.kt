package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
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
class OrderV1Controller(private val orderFacade: OrderFacade) : OrderV1ApiSpec {
    @PostMapping
    override fun createOrder(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody @Valid request: OrderV1Dto.OrderCreateRequest,
    ): ApiResponse<OrderV1Dto.OrderCreateResponse> {
        val appRequest = request.toApplicationRequest()
        return orderFacade.createOrder(userId, appRequest)
            .let { OrderV1Dto.OrderCreateResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getOrders(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<Page<OrderV1Dto.OrderListResponse>> {
        val pageable = PageRequest.of(page, size)
        return orderFacade.getOrders(userId, pageable)
            .map { OrderV1Dto.OrderListResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{orderId}")
    override fun getOrderDetail(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable(value = "orderId") orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderDetailResponse> = orderFacade.getOrderDetail(userId, orderId)
        .let { OrderV1Dto.OrderDetailResponse.from(it) }
        .let { ApiResponse.success(it) }
}
