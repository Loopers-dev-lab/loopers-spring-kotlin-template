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

        newOrder.pay()

        val savedOrder = orderRepository.save(newOrder)

        val paidPayment = Payment.pay(command.userId, savedOrder, command.usePoint)

        paymentRepository.save(paidPayment)

        return savedOrder
    }
}
