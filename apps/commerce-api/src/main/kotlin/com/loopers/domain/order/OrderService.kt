package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.MemberCoupon
import com.loopers.domain.coupon.event.CouponUsedEvent
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.order.event.OrderItemDto
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.event.StockDecreasedEvent
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val memberRepository: MemberRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val couponService: CouponService,
) {
    @Transactional
    fun createOrderWithCalculation(
        memberId: String,
        orderItems: List<OrderItemCommand>,
        couponId: Long? = null,
        usePoint: Long = 0L
    ): Order {
        val productMap = loadProductsWithoutLock(orderItems)
        val (discountAmount, memberCoupon) = applyCouponDiscount(memberId, couponId, orderItems, productMap)
        val order = createOrder(memberId, orderItems, productMap, discountAmount)

        applyPointDiscount(memberId, order, usePoint)

        val savedOrder = orderRepository.save(order)

        publishOrderEvents(savedOrder, couponId, memberCoupon, discountAmount)

        return savedOrder
    }

    private fun applyCouponDiscount(
        memberId: String,
        couponId: Long?,
        orderItems: List<OrderItemCommand>,
        productMap: Map<Long, Product>
    ): Pair<Money, MemberCoupon?> {
        if (couponId == null) return Pair(Money.zero(), null)

        val coupon = couponService.getMemberCoupon(memberId, couponId)
            ?: throw IllegalArgumentException("쿠폰을 찾을 수 없습니다")

        val totalAmount = calculateTotalAmount(orderItems, productMap)
        val discount = couponService.calculateDiscount(coupon, totalAmount)

        coupon.use()

        return Pair(discount, coupon)
    }

    private fun calculateTotalAmount(
        orderItems: List<OrderItemCommand>,
        productMap: Map<Long, Product>
    ): Money {
        return orderItems.sumOf { item ->
            productMap[item.productId]!!.price.amount * item.quantity
        }.let { Money(it) }
    }

    private fun applyPointDiscount(memberId: String, order: Order, usePoint: Long) {
        if (usePoint == 0L) return

        order.validatePointUsage(usePoint)

        val member = memberRepository.findByMemberIdWithLockOrThrow(memberId)
        member.usePoint(usePoint)
    }

    private fun publishOrderEvents(
        savedOrder: Order,
        couponId: Long?,
        memberCoupon: MemberCoupon?,
        discountAmount: Money
    ) {
        publishOrderCreatedEvent(savedOrder, couponId)

        if (couponId != null && memberCoupon != null) {
            eventPublisher.publishEvent(
                CouponUsedEvent(
                    aggregateId = savedOrder.id,
                    orderId = savedOrder.id,
                    memberId = savedOrder.memberId,
                    couponId = couponId,
                    memberCouponId = memberCoupon.id,
                    discountAmount = discountAmount.amount
                )
            )
        }
    }

    private fun createOrder(
        memberId: String,
        orderItems: List<OrderItemCommand>,
        productMap: Map<Long, Product>,
        discountAmount: Money,
    ): Order = Order.create(
        memberId = memberId,
        orderItems = orderItems,
        productMap = productMap,
        discountAmount = discountAmount,
    )

    private fun publishOrderCreatedEvent(savedOrder: Order, couponId: Long?) {
        val orderItems = savedOrder.items.map {
            OrderItemDto(
                productId = it.productId,
                quantity = it.quantity.value,
                price = it.price.amount
            )
        }

        eventPublisher.publishEvent(
            OrderCreatedEvent(
                aggregateId = savedOrder.id,
                orderId = savedOrder.id,
                memberId = savedOrder.memberId,
                orderAmount = savedOrder.totalAmount.amount,
                couponId = couponId,
                orderItems = orderItems,
                createdAt = Instant.now(),
            ),
        )
    }

    /**
     * 결제 완료 후 주문 처리
     * - 재고 차감
     * - 주문 상태를 COMPLETED로 변경
     * - 외부 시스템 연동
     */
    @Transactional
    fun completeOrderWithPayment(orderId: Long) {
        val order = orderRepository.findByIdOrThrow(orderId)

        val productIds = order.items.map { it.productId }
        val products = productRepository.findAllByIdInWithLock(productIds)
        validateProducts(products, productIds)

        val productMap = products.associateBy { it.id }

        order.items.forEach { orderItem ->
            val product = productMap[orderItem.productId]!!
            product.decreaseStock(orderItem.quantity)
            val afterStock = product.stock.quantity

            eventPublisher.publishEvent(
                StockDecreasedEvent(
                    aggregateId = product.id,
                    productId = product.id,
                    orderId = order.id,
                    quantity = orderItem.quantity.value,
                    remainingStock = afterStock,
                )
            )
        }

        order.complete()
    }

    private fun loadProductsWithoutLock(orderItems: List<OrderItemCommand>): Map<Long, Product> {
        val productIds = orderItems.map { it.productId }
        val products = productRepository.findAllByIdIn(productIds)
        validateProducts(products, productIds)

        return products.associateBy { it.id }     
    }

    private fun validateProducts(
        products: List<Product>,
        productIds: List<Long>,
    ) {
        val foundIds = products.map { it.id }.toSet()
        val requestIds = productIds.toSet()
        val missingIds = requestIds - foundIds

        if (missingIds.isNotEmpty()) {
            throw CoreException(
                ErrorType.PRODUCT_NOT_FOUND,
                "상품을 찾을 수 없습니다: productIds=$missingIds",
            )
        }
    }

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): Order {
        return orderRepository.findByIdOrThrow(orderId)
    }

    @Transactional(readOnly = true)
    fun getOrdersByMemberId(memberId: String, pageable: Pageable): Page<Order> {
        return orderRepository.findByMemberId(memberId, pageable)
    }

    @Transactional
    fun failOrder(orderId: Long) {
        val order = orderRepository.findByIdOrThrow(orderId)
        order.fail()
        orderRepository.save(order)
    }
}
