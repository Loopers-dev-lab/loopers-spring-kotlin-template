package com.loopers.domain.order

import com.loopers.domain.coupon.CouponService
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
    @Transactional
    fun createOrder(command: CreateOrderCommand): Order {
        // 상품 조회 (비관적 락 적용 - 재고 동시성 제어)
        val productMap = productRepository.findAllByIdInWithLock(
            command.items.map { it.productId }
        ).associateBy { it.id }

        // 회원 조회 (비관적 락 적용 - 포인트 동시성 제어)
        val member = memberRepository.findByMemberIdWithLockOrThrow(command.memberId)

        // 쿠폰 할인 계산 및 사용 처리
        val discountAmount = couponService.applyAndUseCouponForOrder(
            memberId = command.memberId,
            couponId = command.couponId,
            orderItems = command.items,
            productMap = productMap
        )

        // Order 생성 (상품 검증 및 OrderItem 생성 로직은 Order.create()에 위임)
        val order = Order.create(
            memberId = command.memberId,
            orderItems = command.items,
            productMap = productMap,
            discountAmount = discountAmount,
        )

        // 재고 차감
        decreaseProductStocks(order, productMap)

        // 회원 결제 처리 (포인트 검증 및 차감)
        order.processPayment(member)

        // 주문 저장
        val savedOrder = orderRepository.save(order)

        // 외부 시스템 연동
        externalOrderService.processOrder(savedOrder)
        savedOrder.complete()

        return savedOrder
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
