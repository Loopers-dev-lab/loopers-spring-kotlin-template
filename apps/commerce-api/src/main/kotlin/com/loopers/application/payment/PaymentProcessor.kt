package com.loopers.application.payment

import com.loopers.application.order.OrderStateService
import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
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
        val payment = validatePayment(command.paymentId)
        val order = orderService.get(payment.orderId)
        val orderItems = orderItemService.findAll(order.id)
        val productOptions = loadProductOptions(orderItems)

        try {
            decreaseStocks(orderItems)

            val totalPrice = calculateTotalPrice(orderItems, productOptions)
            pointService.get(order.userId).use(totalPrice.intValueExact())

            payment.success()
            order.success()
        } catch (e: Exception) {
            // TODO: 결제 실패 사유를 담는 별도의 컬럼 생성 후 저장해야함 or 실패 상태도 분리해주면 좋을 듯
            paymentStateService.paymentFailure(payment.id)
            orderStateService.orderFailure(order.id)
            throw e
        }
    }

    private fun validatePayment(paymentId: Long): Payment {
        val payment = paymentService.get(paymentId)
        if (payment.status != Payment.Status.REQUESTED) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제를 요청할 수 없는 상태입니다.")
        }
        return payment
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
        val productIds = productOptions.map { it.productId }.distinct()
        val products = productService.findAll(productIds)

        return orderItems.sumOf { item ->
            val option = productOptions.find { it.id == item.productOptionId }
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 옵션을 찾을 수 없습니다.")

            val product = products.find { it.id == option.productId }
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 정보를 찾을 수 없습니다.")

            (product.price.value + option.additionalPrice.value) * item.quantity.value.toBigDecimal()
        }
    }
}
