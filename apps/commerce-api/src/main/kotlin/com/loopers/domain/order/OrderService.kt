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
        val orderItems = command
            .items
            .map { OrderItem.create(it.productId, it.quantity, it.productName, it.currentPrice) }

        val paidOrder = Order.paid(command.userId, orderItems.toMutableList())

        val savedOrder = orderRepository.save(paidOrder)

        val paidPayment = Payment.paid(command.userId, paidOrder, command.usePoint)

        paymentRepository.save(paidPayment)

        return savedOrder
    }
}
