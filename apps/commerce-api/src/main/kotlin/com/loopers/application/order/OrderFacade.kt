package com.loopers.application.order

import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.transaction.annotation.Transactional

open class OrderFacade(
    val productService: ProductService,
    val orderService: OrderService,
    val pointService: PointService,
) {
    @Transactional
    open fun placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        productService.decreaseStocks(criteria.to())

        pointService.deduct(criteria.userId, criteria.usePoint)

        val productIds = criteria.items.map { it.productId }
        val productMap = productService.findAllByIds(productIds).associateBy { it.id }

        val placeOrderItems = criteria.items.map { item ->
            val product = productMap[item.productId]
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품을 찾을 수 없습니다.")

            OrderCommand.PlaceOrderItem(
                productId = item.productId,
                quantity = item.quantity,
                currentPrice = product.price,
                productName = product.name,
            )
        }.toMutableList()

        val command = OrderCommand.PlaceOrder(
            userId = criteria.userId,
            usePoint = criteria.usePoint,
            items = placeOrderItems,
        )

        val order = orderService.place(command)

        return OrderInfo.PlaceOrder(order.id)
    }
}
