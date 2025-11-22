package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.Money
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderQueryService
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductQueryService
import com.loopers.domain.product.StockService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val orderQueryService: OrderQueryService,
    private val productQueryService: ProductQueryService,
    private val stockService: StockService,
    private val pointService: PointService,
    private val couponService: CouponService,
) {
    @Transactional
    fun createOrder(userId: Long, request: OrderCreateRequest): OrderCreateInfo {
        val orderItems = validateAndCreateOrderItems(request)
        val totalMoney = calculateTotalAmount(orderItems)

        val discountAmount = applyCoupon(userId, request.couponId, totalMoney)
        val finalAmount = totalMoney - discountAmount

        pointService.validateUserPoint(userId, finalAmount)

        val order = orderService.createOrder(userId, orderItems)

        deductStocks(request.items)
        pointService.deductPoint(userId, finalAmount)

        return OrderCreateInfo.from(order)
    }

    private fun applyCoupon(userId: Long, couponId: Long?, totalAmount: Money): Money {
        if (couponId == null) {
            return Money(BigDecimal.ZERO, totalAmount.currency)
        }

        val userCoupon = couponService.useUserCoupon(userId, couponId)
        return userCoupon.coupon.calculateDiscount(totalAmount)
    }

    private fun validateAndCreateOrderItems(
        request: OrderCreateRequest,
    ): List<OrderItem> = request.items.map { item ->
        val productDetail = productQueryService.getProductDetail(item.productId)
        val product = productDetail.product

        stockService.validateStockAvailability(productDetail.stock, product.name, item.quantity)

        createOrderItemSnapshot(product, item.quantity)
    }

    private fun calculateTotalAmount(orderItems: List<OrderItem>): Money {
        if (orderItems.isEmpty()) {
            return Money(BigDecimal.ZERO, com.loopers.domain.product.Currency.KRW)
        }

        val firstCurrency = orderItems.first().priceAtOrder.currency
        if (orderItems.any { it.priceAtOrder.currency != firstCurrency }) {
            throw CoreException(ErrorType.BAD_REQUEST, "모든 주문 항목은 동일한 통화를 사용해야 합니다.")
        }

        val totalAmount = orderItems.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.calculateItemAmount().amount
        }

        return Money(amount = totalAmount, currency = firstCurrency)
    }

    private fun deductStocks(items: List<OrderItemRequest>) {
        items.sortedBy { it.productId }.forEach { item ->
            stockService.decreaseStock(item.productId, item.quantity)
        }
    }

    private fun createOrderItemSnapshot(
        product: Product,
        quantity: Int,
    ): OrderItem = OrderItem(
        productId = product.id,
        productName = product.name,
        brandId = product.brand.id,
        brandName = product.brand.name,
        brandDescription = product.brand.description,
        quantity = quantity,
        priceAtOrder = product.price,
    )

    fun getOrders(
        userId: Long,
        pageable: Pageable,
    ): Page<OrderListInfo> = orderQueryService
        .getOrders(userId, pageable)
        .map { OrderListInfo.from(it) }

    fun getOrderDetail(
        userId: Long,
        orderId: Long,
    ): OrderDetailInfo = OrderDetailInfo.from(
        orderQueryService.getOrderDetail(userId, orderId),
    )
}
