package com.loopers.domain.order

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
) {

    @Transactional
    fun createOrder(userId: Long, totalAmount: BigDecimal): Order {
        val order = Order.of(userId = userId, totalAmount = totalAmount)
        return orderRepository.save(order)
    }

    @Transactional
    fun createOrderItems(orderId: Long, items: List<CreateOrderCommand>): List<OrderItem> {
        val orderItems = items.map { item ->
            OrderItem.of(
                orderId = orderId,
                productId = item.productId,
                productName = item.productName,
                brandName = item.brandName,
                price = item.price,
                quantity = item.quantity,
            )
        }
        return orderItemRepository.saveAll(orderItems)
    }
}
