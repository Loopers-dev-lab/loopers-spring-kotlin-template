package com.loopers.application.order

import com.loopers.domain.brand.BrandService
import com.loopers.domain.order.CreateOrderCommand
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.domain.stock.StockService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class OrderFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val stockService: StockService,
    private val pointService: PointService,
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {

    @Transactional
    fun createOrder(userId: Long, items: List<OrderItemCommand>): Order {
        val productIds = items.map { it.productId }
        val productMap = productService.getProductById(productIds)

        val brandIds = productMap.values.map { it.brandId }.distinct()
        val brandMap = brandService.getBrandById(brandIds)

        val orderCommands = items.map { item ->
            val product = productMap[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다. [productId: ${item.productId}]")

            val brand = brandMap[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다. [brandId: ${product.brandId}]")

            stockService.deductStock(product.id, item.quantity)

            CreateOrderCommand(
                productId = product.id,
                productName = product.name,
                brandName = brand.name,
                price = product.price,
                quantity = item.quantity,
            )
        }

        val totalAmount = orderCommands.sumOf { it.price.multiply(BigDecimal(it.quantity)) }

        pointService.deductPoint(userId, totalAmount)

        val order = orderService.createOrder(userId, totalAmount)
        orderService.createOrderItems(order.id, orderCommands)

        paymentService.createPayment(order.id, userId, totalAmount)

        return order
    }
}
