package com.loopers.domain.payment

import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.entity.Payment.Status
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductOptionService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStockService
import com.loopers.domain.product.dto.command.ProductStockCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentProcessor(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val orderItemService: OrderItemService,
    private val productService: ProductService,
    private val productOptionService: ProductOptionService,
    private val productStockService: ProductStockService,
    private val pointService: PointService,
) {
    fun process(command: PaymentCommand.Process): Status {
        val payment = paymentService.get(command.paymentId)
        if (payment.status != Status.REQUESTED) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "잘못된 요청입니다.")
        }

        val order = orderService.get(payment.orderId)
        val orderItems = orderItemService.findAll(order.id)

        // 재고 처리
        val productOptionIds = orderItems.map { it.productOptionId }
        val productOptions = productOptionService.findAll(productOptionIds)
        val productStocks = productStockService.findAll(productOptionIds)

        val decreaseStocksCommand = ProductStockCommand.DecreaseStocks(
            orderItems.map {
                ProductStockCommand.DecreaseStocks.DecreaseStock(it.productOptionId, it.quantity.value)
            },
        )
        productStockService.decreaseStock(decreaseStocksCommand)

        // 결제 처리
        val productIds = productOptions.map { it.productId }.distinct()
        val products = productService.findAll(productIds)

        val totalPrice = orderItems.sumOf { item ->
            val option = productOptions.find { it.id == item.productOptionId }
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 옵션을 찾을 수 없습니다.")

            val product = products.find { it.id == option.productId }
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 정보를 찾을 수 없습니다.")

            (product.price.value + option.additionalPrice.value) * item.quantity.value.toBigDecimal()
        }

        val point = pointService.get(order.userId)
        point.use(totalPrice.intValueExact())

        payment.success()
        return payment.status
    }
}
