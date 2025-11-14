package com.loopers.application.order

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
        val totalPrice = command.orderItems.sumOf { it.productPrice }

        try {
            pointService.pay(userId, totalPrice)
        } catch (e: IllegalArgumentException) {
            throw CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다: ${e.message}")
        }

        try {
            productService.occupyStocks(command)
        } catch (e: IllegalArgumentException) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다: ${e.message}")
        }

        // 주문 생성
        return orderService.order(userId, command)
    }
}
