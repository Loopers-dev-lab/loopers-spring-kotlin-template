package com.loopers.application.order

import com.loopers.domain.member.MemberService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val memberService: MemberService,
) {
    /**
     * 주문 생성
     * 
     * 1. 주문 생성 및 저장 (트랜잭션 1)
     * 2. 결제 처리 (트랜잭션 2 - 별도)
     *    - 포인트 전액: 즉시 완료
     *    - 카드 결제: PG 호출 (실패해도 주문은 PENDING 유지)
     */
    fun createOrder(
        memberId: String,
        request: com.loopers.interfaces.api.order.OrderV1Dto.CreateOrderRequest
    ): OrderInfo {
        val usePoint = request.usePoint ?: 0L
        
        // 1. 주문 생성 및 저장 (트랜잭션 1)
        val order = orderService.createOrderWithCalculation(
            memberId = memberId,
            orderItems = request.items.map { it.toCommand() },
            couponId = request.couponId,
            usePoint = usePoint
        )

        // 2. 결제 처리 (트랜잭션 2)
        val finalPaymentAmount = order.finalAmount.amount - usePoint

        val resultStatus = if (finalPaymentAmount == 0L) {
            // 포인트 전액 결제 - 즉시 완료
            orderService.completeOrderWithPayment(order.id!!)
            OrderStatus.COMPLETED
        } else {
            // 카드 결제 - PG 호출 (실패해도 주문은 PENDING 유지)
            try {
                paymentService.requestCardPayment(
                    order = order,
                    userId = memberId,
                    cardType = request.cardType ?: throw IllegalArgumentException("cardType required"),
                    cardNo = request.cardNo ?: throw IllegalArgumentException("cardNo required"),
                    amount = finalPaymentAmount
                )
            } catch (e: Exception) {
                // PG 실패 시 포인트만 롤백 (주문은 PENDING 유지)
                if (usePoint > 0) {
                    memberService.rollbackPoint(memberId, usePoint)
                }
            }
            OrderStatus.PENDING
        }

        return OrderInfo(
            id = order.id,
            memberId = order.memberId,
            status = resultStatus,
            totalAmount = order.totalAmount.amount,
            discountAmount = order.discountAmount.amount,
            finalAmount = order.finalAmount.amount,
            items = order.items.map { OrderItemInfo.from(it) },
            createdAt = order.createdAt.toString()
        )
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
