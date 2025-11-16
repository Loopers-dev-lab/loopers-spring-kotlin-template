package com.loopers.domain.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderRepository {
    fun findAllBy(userId: Long, pageable: Pageable): Page<Order>
    fun findById(id: Long): Order?
    fun findAllOrderDetailBy(orderId: Long): List<OrderDetail>
    fun save(order: Order): Order
    fun saveAllOrderDetail(orderDetails: List<OrderDetail>): List<OrderDetail>
}
