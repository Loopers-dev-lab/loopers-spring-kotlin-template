package com.loopers.application.order

import com.loopers.domain.order.OrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
) {
    /**
     * 주문 생성
     * - 쿠폰 할인, 포인트 사용 후 최종 금액 계산
     * - 최종 금액이 0원이면 포인트 전액 결제
     * - 최종 금액이 0원 이상이면 카드 결제
     */
    fun createOrder(
        memberId: String,
        request: com.loopers.interfaces.api.order.OrderV1Dto.CreateOrderRequest
    ): OrderInfo {
        val order = orderService.createOrder(
            memberId = memberId,
            orderItems = request.items.map { it.toCommand() },
            couponId = request.couponId,
            usePoint = request.usePoint ?: 0L,
            cardType = request.cardType,
            cardNo = request.cardNo
        )

        return OrderInfo.from(order)
    }

    /**
     * 주문 조회
     */
    fun getOrder(orderId: Long): OrderInfo {
        val order = orderService.getOrder(orderId)
        return OrderInfo.from(order)
    }

    /**
     * 주문 목록 조회
     */
    fun getOrders(memberId: String, pageable: Pageable): Page<OrderInfo> {
        val orders = orderService.getOrdersByMemberId(memberId, pageable)
        return OrderInfo.fromPage(orders)
    }
}
