package com.loopers.application.payment

import com.loopers.application.order.OrderStateService
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.entity.Order
import com.loopers.domain.order.entity.OrderItem
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductStockService
import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.domain.product.dto.result.ProductStockResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentProcessor(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val orderItemService: OrderItemService,
    private val productStockService: ProductStockService,
    private val pointService: PointService,
    private val paymentStateService: PaymentStateService,
    private val orderStateService: OrderStateService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun process(id: Long) {
        val payment = paymentService.get(id)
        val order = orderService.get(payment.orderId)

        val orderItems = orderItemService.findAll(order.id)

        try {
            processPayment(order, payment, orderItems)
        } catch (e: Exception) {
            processFailure(order.id, payment.id, resolveFailureReason(e))
            throw e
        }
    }

    private fun processPayment(order: Order, payment: Payment, orderItems: List<OrderItem>) {
        val decreaseStocks = getDecreaseStocks(orderItems)
        productStockService.decreaseStocks(decreaseStocks.toCommand())

        val point = pointService.get(order.userId)
        point.use(payment.paymentPrice.value)

        payment.success()
        order.success()
    }

    private fun processFailure(orderId: Long, paymentId: Long, reason: String) {
        paymentStateService.paymentFailure(paymentId, reason)
        orderStateService.orderFailure(orderId, reason)
    }

    private fun getDecreaseStocks(orderItems: List<OrderItem>): ProductStockResult.DecreaseStocks {
        val command = ProductStockCommand.GetDecreaseStock(
            orderItems.map {
                ProductStockCommand.GetDecreaseStock.DecreaseStock(it.productOptionId, it.quantity.value)
            },
        )
        return productStockService.getDecreaseStock(command)
    }

    private fun resolveFailureReason(e: Throwable): String {
        return when (e) {
            is CoreException -> when (e.errorType) {
                ErrorType.POINT_NOT_ENOUGH -> "포인트가 부족합니다."
                ErrorType.PRODUCT_STOCK_NOT_ENOUGH -> "재고가 부족합니다."
                else -> "알 수 없는 도메인 예외가 발생했습니다."
            }
            is ObjectOptimisticLockingFailureException -> "동시 요청으로 인한 에러가 발생했습니다."
            else -> "알 수 없는 예외가 발생했습니다."
        }
    }
}
