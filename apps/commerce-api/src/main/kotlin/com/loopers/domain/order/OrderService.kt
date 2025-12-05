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
    private val paymentService: com.loopers.domain.payment.PaymentService,
) {
    @Transactional
    fun createOrder(
        memberId: String,
        orderItems: List<OrderItemCommand>,
        couponId: Long? = null,
        usePoint: Long = 0L,
        cardType: String? = null,
        cardNo: String? = null
    ): Order {
        // 1. 상품 및 회원 조회
        val productMap = loadProducts(orderItems)
        val member = memberRepository.findByMemberIdWithLockOrThrow(memberId)

        // 2. 주문 생성 (쿠폰 할인 포함)
        val order = createOrderWithCoupon(memberId, orderItems, couponId, productMap)

        // 3. 포인트 사용 및 최종 결제 금액 계산
        val finalPaymentAmount = calculateFinalPaymentAmount(order, usePoint, member)

        // 4. 주문 저장
        val savedOrder = orderRepository.save(order)

        // 5. 결제 처리 (포인트 전액 or 카드 결제)
        if (finalPaymentAmount == 0L) {
            completePointPayment(savedOrder, productMap)
        } else {
            processCardPayment(savedOrder, memberId, finalPaymentAmount, cardType, cardNo, usePoint, member)
        }

        return savedOrder
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

    private fun calculateFinalPaymentAmount(order: Order, usePoint: Long, member: Member): Long {
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

        return finalPaymentAmount
    }

    private fun completePointPayment(order: Order, productMap: Map<Long, Product>) {
        decreaseProductStocks(order, productMap)
        order.complete()
        externalOrderService.processOrder(order)
    }

    private fun processCardPayment(
        order: Order,
        memberId: String,
        amount: Long,
        cardType: String?,
        cardNo: String?,
        usePoint: Long,
        member: Member
    ) {
        if (cardType == null || cardNo == null) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "카드 결제가 필요합니다. cardType과 cardNo를 입력해주세요."
            )
        }

        try {
            paymentService.requestCardPayment(
                order = order,
                userId = memberId,
                cardType = cardType,
                cardNo = cardNo,
                amount = amount
            )
        } catch (e: CoreException) {
            rollbackPaymentFailure(order, usePoint, member)
            throw e
        }
    }

    private fun rollbackPaymentFailure(order: Order, usePoint: Long, member: Member) {
        if (usePoint > 0) {
            member.chargePoint(usePoint)
        }
        order.fail()
        orderRepository.save(order)
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
