package com.loopers.application.order

import com.loopers.cache.CacheTemplate
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 오케스트레이션 Facade
 *
 * placeOrder() 플로우:
 * 1. 상품 조회 및 주문 생성 (OrderCreatedEventV1 발행 -> BEFORE_COMMIT에서 재고 차감)
 * 2. 쿠폰 사용 (있는 경우)
 * 3. 포인트 차감 (있는 경우)
 * 4. 결제 생성 (PaymentCreatedEventV1 발행 -> ASYNC로 PG 결제 요청)
 * 5. 즉시 응답 (PENDING 상태)
 *
 * PG 결제 실패 시 PaymentFailedEventV1 체인으로 자동 복구:
 * - 포인트 복원, 쿠폰 복원
 * - 주문 취소 (OrderCanceledEventV1 발행 -> BEFORE_COMMIT에서 재고 복원)
 */
@Component
class OrderFacade(
    val productService: ProductService,
    val orderService: OrderService,
    val pointService: PointService,
    val couponService: CouponService,
    val paymentService: PaymentService,
    private val cacheTemplate: CacheTemplate,
) {
    /**
     * 주문을 생성합니다.
     *
     * 트랜잭션 내에서 주문, 쿠폰, 포인트, 결제를 처리하고 즉시 응답합니다.
     * 재고 차감은 OrderCreatedEventV1 리스너(BEFORE_COMMIT)에서 처리됩니다.
     * PG 결제 요청은 PaymentCreatedEventV1 리스너(ASYNC)에서 처리됩니다.
     *
     * @param criteria 주문 생성 조건
     * @return 주문 정보 (PENDING 상태의 결제 포함)
     */
    @Transactional
    fun placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        // 1. 카드 정보 생성
        val cardInfo = criteria.cardType?.let { cardType ->
            criteria.cardNo?.let { cardNo ->
                CardInfo(
                    cardType = cardType,
                    cardNo = cardNo,
                )
            }
        }

        // 2. 상품 조회
        val productIds = criteria.items.map { it.productId }
        val products = productService.findAllByIds(productIds)
        val productMap = products.associateBy { it.id }

        // 3. 주문 생성
        val placeOrderItems = criteria.items.map { item ->
            val product = productMap[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")

            OrderCommand.PlaceOrderItem(
                productId = item.productId,
                quantity = item.quantity,
                currentPrice = product.price,
                productName = product.name,
            )
        }

        val placeCommand = OrderCommand.PlaceOrder(
            userId = criteria.userId,
            items = placeOrderItems,
        )

        val order = orderService.place(placeCommand)

        // 4. 쿠폰 사용 (있는 경우)
        val orderAmount = order.totalAmount
        val couponDiscount = criteria.issuedCouponId?.let { issuedCouponId ->
            couponService.useCoupon(criteria.userId, issuedCouponId, orderAmount)
        } ?: Money.ZERO_KRW

        // 5. 포인트 차감 (0원 초과인 경우에만)
        if (criteria.usePoint > Money.ZERO_KRW) {
            pointService.deduct(criteria.userId, criteria.usePoint)
        }

        // 6. 결제 생성 (PaymentCreatedEventV1 발행 -> ASYNC로 PG 결제 요청)
        val payment = paymentService.create(
            PaymentCommand.Create(
                userId = criteria.userId,
                orderId = order.id,
                totalAmount = order.totalAmount,
                usedPoint = criteria.usePoint,
                issuedCouponId = criteria.issuedCouponId,
                couponDiscount = couponDiscount,
                cardInfo = cardInfo,
            ),
        )

        // 7. 즉시 응답 (PENDING 상태, PG 결제는 비동기로 진행)
        return OrderInfo.PlaceOrder(
            orderId = order.id,
            paymentId = payment.id,
            paymentStatus = payment.status,
        )
    }
}
