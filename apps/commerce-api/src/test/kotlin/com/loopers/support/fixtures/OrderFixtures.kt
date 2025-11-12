package com.loopers.support.fixtures

import com.loopers.domain.brand.Brand
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.Product
import java.time.ZonedDateTime

object OrderFixtures {

    fun createOrder(
        id: Long = 1L,
        status: OrderStatus = OrderStatus.PENDING,
        totalAmount: Long = 10000L,
        userId: Long = 1L,
    ): Order {
        return Order.create(
            totalAmount = totalAmount,
            userId = userId,
        ).apply {
            when (status) {
                OrderStatus.COMPLETED -> complete()
                OrderStatus.CANCELLED -> cancel()
                else -> {}
            }
        }.withId(id).withCreatedAt(ZonedDateTime.now())
    }

    fun createOrderDetail(
        id: Long = 1L,
        quantity: Long = 10,
        brand: Brand,
        product: Product,
        order: Order,
    ): OrderDetail {
        return OrderDetail.create(
            quantity = quantity,
            brand = brand,
            product = product,
            order = order,
        ).withId(id).withCreatedAt(ZonedDateTime.now())
    }
}
