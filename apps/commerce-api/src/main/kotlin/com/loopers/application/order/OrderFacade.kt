package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val pointService: PointService,
    private val productService: ProductService,
    private val couponService: CouponService,
) {

    @Transactional
    fun order(userId: Long, couponId: Long?, command: OrderCommand): OrderModel {
        val totalPrice = command.orderItems.sumOf { it.productPrice * it.quantity.toBigDecimal() }

        val discountPrice = couponId?.let {
            couponService.calculateDiscountPrice(it, totalPrice)
        } ?: BigDecimal.ZERO

        pointService.pay(userId, totalPrice.minus(discountPrice))

        productService.occupyStocks(command)
        // 주문 생성
        return orderService.order(userId, command)
    }
}
