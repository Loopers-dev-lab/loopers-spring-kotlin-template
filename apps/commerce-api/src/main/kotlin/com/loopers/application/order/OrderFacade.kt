package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.PaymentDto
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val couponService: CouponService,
    private val paymentService: PaymentService,
) {

    @Transactional
    fun order(userId: Long, command: OrderCommand): OrderModel {
        // 1. 가격 계산
        val totalPrice = command.orderItems.sumOf { it.productPrice * it.quantity.toBigDecimal() }

        val discountPrice = command.couponId?.let {
            couponService.calculateDiscountPrice(it, userId, totalPrice)
        } ?: totalPrice

        // 2. 재고 차감
        productService.occupyStocks(command)

        // 3. 주문 생성 (PENDING)
        val order = orderService.prepare(userId, command)

        // 4. 결제 요청
        when (
            val result = paymentService.pay(
                userId,
                PaymentDto.Request.from(order.orderKey, command.cardType, command.cardNo, discountPrice),
            )
        ) {
            is PaymentDto.Result.Success -> {
                return orderService.requestPayment(order)
            }

            is PaymentDto.Result.Failed -> {
                throw CoreException(ErrorType.INTERNAL_ERROR, result.errorMessage)
            }
        }
    }
}
