package com.loopers.domain.order

interface OrderRepository {

    fun save(order: OrderModel): OrderModel

    fun findByOrderId(orderId: Long): OrderModel?

    fun findByOrderKey(orderKey: String): OrderModel?

    fun findByStatus(status: OrderStatus): List<OrderModel>
}
