package com.loopers.domain.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentCallbackService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productService: ProductService
) {
    @Transactional
    fun handlePaymentCallback(callback: PaymentCallbackDto) {
        val payment = paymentRepository.findByTransactionKey(callback.transactionKey)
            ?: throw CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제 정보를 찾을 수 없습니다.")

        if (payment.status != PaymentStatus.PENDING) {
            return
        }

        val order = orderRepository.findByIdOrThrow(payment.orderId)

        if (callback.isSuccess()) {
            productService.decreaseStockByOrder(order)
            payment.markAsSuccess()
            order.complete()
        }
    }
}
