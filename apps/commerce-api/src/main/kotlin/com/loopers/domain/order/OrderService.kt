package com.loopers.domain.order

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
}
