package com.loopers.application.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val pointService: PointService,
    private val productService: ProductService,
) {

    @Transactional
    fun order(userId: Long, command: OrderCommand): OrderModel {
        val totalPrice = command.orderItems.sumOf { it.productPrice * it.quantity.toBigDecimal() }

        pointService.pay(userId, totalPrice)

        productService.occupyStocks(command)

        // 주문 생성
        return orderService.order(userId, command)
    }
}
