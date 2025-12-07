package com.loopers.domain.order

import com.loopers.application.order.OrderCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class OrderService(private val orderRepository: OrderRepository) {

    fun prepare(userId: Long, orderCommand: OrderCommand) = orderRepository.save(OrderModel.order(userId, orderCommand))

    fun requestPayment(order: OrderModel): OrderModel {
        order.requestPayment()
        return orderRepository.save(order)
    }

    fun updateOrderByStatus(orderKey: String, status: String) {
        val order = orderRepository.findByOrderKey(orderKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다.")

        when (status) {
            "SUCCESS" -> order.complete()
            else -> order.fail() // 그 외 모두 실패 처리
        }
        orderRepository.save(order)
    }

    fun findByStatus(status: OrderStatus): List<OrderModel> = orderRepository.findByStatus(status)
}
