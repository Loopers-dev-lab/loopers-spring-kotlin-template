package com.loopers.domain.order

import com.loopers.domain.product.Product
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
        val order = orderRepository.findById(id) ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다: $id")

        order.validateOwner(userId)

        return order
    }

    @Transactional(readOnly = true)
    fun getOrderDetail(orderId: Long): List<OrderDetail> {
        return orderRepository.findAllOrderDetailBy(orderId)
    }

    fun calculateTotalAmount(
        items: List<OrderCommand.OrderDetailCommand>,
        products: List<Product>,
    ): Long {
        val productPriceMap = products.associateBy({ it.id }, { it.price })

        return items.sumOf { item ->
            val price = productPriceMap[item.productId]
                ?: throw IllegalArgumentException("상품 ID ${item.productId}에 해당하는 상품을 찾을 수 없습니다.")
            price * item.quantity
        }
    }

    @Transactional
    fun createOrder(param: OrderCommand.Create) {
        val order = orderRepository.save(
            Order.create(
                totalAmount = param.totalAmount,
                userId = param.userId,
            ),
        )

        val orderDetails = OrderDetail.create(
            items = param.items,
            brands = param.brands,
            products = param.products,
            order = order,
        )
        orderRepository.saveAllOrderDetail(orderDetails)
    }
}
