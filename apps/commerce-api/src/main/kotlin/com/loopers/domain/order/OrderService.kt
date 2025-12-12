package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.order.event.OrderCreatedEvent
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.order.ExternalOrderService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val memberRepository: MemberRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val couponService: CouponService,
    private val externalOrderService: ExternalOrderService,
) {
    /**
     * 주문 생성 및 계산 (쿠폰 할인, 포인트 사용 검증)
     * 순수 도메인 로직만 처리
     */
    @Transactional
    fun createOrderWithCalculation(
        memberId: String,
        orderItems: List<OrderItemCommand>,
        couponId: Long? = null,
        usePoint: Long = 0L
    ): Order {
        // 주문 생성 시에는 Product를 읽기만 하므로 락 불필요 (재고 차감은 결제 완료 시)
        val productMap = loadProductsWithoutLock(orderItems)
        // Member는 포인트 차감이 발생하므로 비관적 락 필요
        val member = memberRepository.findByMemberIdWithLockOrThrow(memberId)

        // 쿠폰 할인 계산 및 사용 처리
        val discountAmount = couponService.applyAndUseCouponForOrder(
            memberId = memberId,
            couponId = couponId,
            orderItems = orderItems,
            productMap = productMap
        )

        // 주문 생성
        val order = createOrder(memberId, orderItems, productMap, discountAmount)

        // 포인트 사용 검증 및 차감
        val finalPaymentAmount = order.finalAmount.amount - usePoint
        if (finalPaymentAmount < 0) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "포인트를 너무 많이 사용했습니다. 최종 금액: ${order.finalAmount.amount}, 사용 포인트: $usePoint"
            )
        }

        if (usePoint > 0) {
            member.usePoint(usePoint)
        }

        val savedOrder = orderRepository.save(order)

        if (couponId != null) {
            publishOrderCreatedEvent(savedOrder, memberId, couponId)
        }

        return savedOrder
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

    private fun publishOrderCreatedEvent(savedOrder: Order, memberId: String, couponId: Long) {
        eventPublisher.publishEvent(
            OrderCreatedEvent(
                orderId = savedOrder.id,
                memberId = memberId,
                orderAmount = savedOrder.totalAmount.amount,
                couponId = couponId,
                createdAt = java.time.LocalDateTime.now().toString(),
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
        val productMap = productRepository.findAllByIdInWithLock(
            order.items.map { it.productId }
        ).associateBy { it.id }

        decreaseProductStocks(order, productMap)
        order.complete()
        externalOrderService.processOrder(order)
    }

    /**
     * 주문 생성 시 상품 조회 (락 없음 - 가격 조회용)
     */
    private fun loadProductsWithoutLock(orderItems: List<OrderItemCommand>): Map<Long, Product> {
        return productRepository.findAllByIdIn(orderItems.map { it.productId })
            .associateBy { it.id }
    }

    /**
     * 재고 차감 시 상품 조회 (락 필요)
     */
    private fun loadProductsWithLock(orderItems: List<OrderItemCommand>): Map<Long, Product> {
        return productRepository.findAllByIdInWithLock(orderItems.map { it.productId })
            .associateBy { it.id }
    }



    private fun decreaseProductStocks(order: Order, productMap: Map<Long, Product>) {
        order.items.forEach { item ->
            val product = productMap[item.productId]
                ?: throw CoreException(
                    ErrorType.PRODUCT_NOT_FOUND,
                    "상품을 찾을 수 없습니다. id: ${item.productId}"
                )
            product.decreaseStock(item.quantity)
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
}
