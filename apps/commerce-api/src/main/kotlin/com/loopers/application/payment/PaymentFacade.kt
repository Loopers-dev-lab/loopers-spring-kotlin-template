package com.loopers.application.payment

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.dto.PaymentCommand
import com.loopers.domain.product.ProductService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val productService: ProductService,
    private val couponService: CouponService,
) {

    private val log = LoggerFactory.getLogger(PaymentFacade::class.java)

    @Transactional
    fun callback(command: PaymentCommand.Callback) {
        val orderId = command.orderId.toLong()

        when (command.status) {
            PaymentStatus.PENDING -> {
                log.info("Payment callback received with PENDING status for orderId: $orderId")
            }

            PaymentStatus.SUCCESS -> {
                orderService.complete(orderId)
                paymentService.success(orderId)
                val orderDetails = orderService.getOrderDetail(orderId)
                productService.deductAllStock(orderDetails)
            }

            PaymentStatus.FAILED -> {
                val order = orderService.getById(orderId)
                orderService.fail(orderId)
                paymentService.fail(orderId, command.reason)
                couponService.rollback(order.userId, order.couponId)
            }
        }
    }
}
