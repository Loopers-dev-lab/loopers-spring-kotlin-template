package com.loopers.application.payment

import com.loopers.domain.order.OrderItemService
import com.loopers.domain.order.entity.OrderItem
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.command.PaymentCommand
import com.loopers.domain.payment.dto.result.PaymentResult
import com.loopers.domain.product.ProductOptionService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.entity.ProductOption
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Transactional(readOnly = true)
@Component
class PaymentFacade(
    private val paymentProcessor: PaymentProcessor,
    private val paymentService: PaymentService,
    private val orderItemService: OrderItemService,
    private val productOptionService: ProductOptionService,
    private val productService: ProductService,
) {
    @Transactional
    fun requestPayment(command: PaymentCommand.Request): PaymentResult.PaymentDetail {
        val orderItems = orderItemService.findAll(command.orderId)
        val productOptions = loadProductOptions(orderItems)
        val paymentPrice = calculateTotalPrice(orderItems, productOptions)
        return PaymentResult.PaymentDetail.from(paymentService.request(command.toEntity(paymentPrice)))
    }

    @Transactional
    fun processPayment(command: PaymentCommand.Process) {
        paymentProcessor.process(command)
    }

    private fun loadProductOptions(orderItems: List<OrderItem>): List<ProductOption> {
        val productOptionIds = orderItems.map { it.productOptionId }
        return productOptionService.findAll(productOptionIds)
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
}
