package com.loopers.application.payment

import com.loopers.application.order.OrderStateService
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.entity.Order
import com.loopers.domain.order.entity.OrderItem
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductOptionService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStockService
import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.domain.product.entity.ProductOption
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class PaymentProcessor(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val orderItemService: OrderItemService,
    private val productService: ProductService,
    private val productOptionService: ProductOptionService,
    private val productStockService: ProductStockService,
    private val pointService: PointService,
    private val paymentStateService: PaymentStateService,
    private val orderStateService: OrderStateService,
) {
    @Transactional
    fun process(command: PaymentCommand.Process) {
        val payment = paymentService.get(command.paymentId)
        payment.validateRequestable()

        val order = orderService.get(payment.orderId)

        runCatching {
            processPayment(order, payment)
        }.getOrElse {
            processFailure(order.id, payment.id, resolveFailureReason(it))
            throw it
        }
    }

    private fun processPayment(
        order: Order,
        payment: Payment,
    ) {
        val orderItems = orderItemService.findAll(order.id)
        decreaseStocks(orderItems)

        val productOptions = loadProductOptions(orderItems)
        val totalPrice = calculateTotalPrice(orderItems, productOptions)

        val point = pointService.get(order.userId)
        point.use(totalPrice.intValueExact())

        payment.success()
        order.success()
    }

    private fun processFailure(orderId: Long, paymentId: Long, reason: String) {
        paymentStateService.paymentFailure(paymentId, reason)
        orderStateService.orderFailure(orderId, reason)
    }

    private fun loadProductOptions(orderItems: List<OrderItem>): List<ProductOption> {
        val productOptionIds = orderItems.map { it.productOptionId }
        return productOptionService.findAll(productOptionIds)
    }

    private fun decreaseStocks(orderItems: List<OrderItem>) {
        val command = ProductStockCommand.DecreaseStocks(
            orderItems.map {
                ProductStockCommand.DecreaseStocks.DecreaseStock(it.productOptionId, it.quantity.value)
            },
        )
        productStockService.decreaseStock(command)
    }

    private fun calculateTotalPrice(
        orderItems: List<OrderItem>,
        productOptions: List<ProductOption>,
    ): BigDecimal {
        val optionMap = productOptions.associateBy { it.id }
        val productIds = productOptions.map { it.productId }.distinct()
        val productMap = productService.findAll(productIds).associateBy { it.id }

        return orderItems.sumOf { item ->
            val option = optionMap[item.productOptionId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 옵션을 찾을 수 없습니다.")
            val product = productMap[option.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 정보를 찾을 수 없습니다.")

            item.calculatePrice(product.price.value, option.additionalPrice.value)
        }
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
