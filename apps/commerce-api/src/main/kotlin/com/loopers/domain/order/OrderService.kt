package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
) {
    @Transactional
    fun place(command: OrderCommand.PlaceOrder): Order {
        val newOrder = Order.place(command.userId)

        command
            .items
            .forEach {
                newOrder.addOrderItem(it.productId, it.quantity, it.productName, it.currentPrice)
            }

        return orderRepository.save(newOrder)
    }

    @Transactional
    fun pay(command: OrderCommand.Pay): Payment {
        val order = orderRepository.findById(command.orderId)
            ?: throw com.loopers.support.error.CoreException(
                com.loopers.support.error.ErrorType.NOT_FOUND,
                "주문을 찾을 수 없습니다.",
            )

        order.pay()

        orderRepository.save(order)

        val payment = Payment.pay(
            userId = command.userId,
            order = order,
            usedPoint = command.usePoint,
            issuedCouponId = command.issuedCouponId,
            couponDiscount = command.couponDiscount,
        )

        return paymentRepository.save(payment)
    }

    /**
     * 주문을 취소합니다. 결제 실패 시 주문 상태를 CANCELLED로 변경합니다.
     *
     * @param orderId 취소할 주문 ID
     * @return 취소된 주문
     * @throws CoreException 주문을 찾을 수 없거나 취소할 수 없는 상태인 경우
     */
    @Transactional
    fun cancelOrder(orderId: Long): Order {
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(
                ErrorType.NOT_FOUND,
                "주문을 찾을 수 없습니다.",
            )

        order.cancel()

        return orderRepository.save(order)
    }
}
