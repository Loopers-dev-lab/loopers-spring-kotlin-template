package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional(readOnly = true)
    fun getOrders(userId: Long, pageable: Pageable): Page<Order> {
        return orderRepository.findAllBy(userId, pageable)
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long, userId: Long): Order {
        val order = orderRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: $userId")

        order.validateOwner(userId)

        return order
    }

    @Transactional(readOnly = true)
    fun getOrderDetail(orderId: Long): List<OrderDetail> {
        return orderRepository.findAllOrderDetailBy(orderId)
    }

    @Transactional
    fun createOrder(param: OrderCommand.Create) {
        val order = orderRepository.save(
            Order.create(
                totalAmount = param.totalAmount,
                userId = param.userId,
            ),
        )

        val brandMap = param.brands.associateBy { it.id }
        val productMap = param.products.associateBy { it.id }

        val orderDetails = param.items.map { item ->
            val product = productMap[item.productId]!!
            val brand = brandMap[product.brandId]!!

            OrderDetail.create(
                quantity = item.quantity,
                brand = brand,
                product = product,
                order = order,
            )
        }
        orderRepository.saveAllOrderDetail(orderDetails)
    }
}
