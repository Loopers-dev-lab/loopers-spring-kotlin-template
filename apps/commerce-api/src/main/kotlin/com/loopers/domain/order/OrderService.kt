package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.infrastructure.order.ExternalOrderService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val memberRepository: MemberRepository,
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
        val productMap = loadProducts(orderItems)
        val member = memberRepository.findByMemberIdWithLockOrThrow(memberId)

        // 쿠폰 할인 적용
        val order = createOrderWithCoupon(memberId, orderItems, couponId, productMap)

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

        return orderRepository.save(order)
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

    private fun loadProducts(orderItems: List<OrderItemCommand>): Map<Long, Product> {
        return productRepository.findAllByIdInWithLock(orderItems.map { it.productId })
            .associateBy { it.id }
    }

    private fun createOrderWithCoupon(
        memberId: String,
        orderItems: List<OrderItemCommand>,
        couponId: Long?,
        productMap: Map<Long, Product>
    ): Order {
        val discountAmount = couponService.applyAndUseCouponForOrder(
            memberId = memberId,
            couponId = couponId,
            orderItems = orderItems,
            productMap = productMap
        )

        return Order.create(
            memberId = memberId,
            orderItems = orderItems,
            productMap = productMap,
            discountAmount = discountAmount
        )
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
