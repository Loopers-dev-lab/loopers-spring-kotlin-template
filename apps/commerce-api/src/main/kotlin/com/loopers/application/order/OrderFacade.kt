package com.loopers.application.order

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    val productService: ProductService,
    val orderService: OrderService,
    val pointService: PointService,
    val couponService: CouponService,
) {
    private val logger = LoggerFactory.getLogger(OrderFacade::class.java)
    private lateinit var retryTemplate: RetryTemplate

    @PostConstruct
    fun init() {
        this.retryTemplate = RetryTemplate.builder()
            .maxAttempts(2)
            .uniformRandomBackoff(100, 500)
            .retryOn(ObjectOptimisticLockingFailureException::class.java)
            .withListener(
                object : RetryListener {
                    override fun <T : Any?, E : Throwable?> onError(
                        context: RetryContext,
                        callback: RetryCallback<T, E>,
                        throwable: Throwable,
                    ) {
                        logger.warn(
                            "낙관적 락 충돌 발생 - 재시도 횟수: {}",
                            context.retryCount,
                        )
                    }
                },
            )
            .build()
    }

    @Transactional
    fun placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        return retryTemplate.execute<OrderInfo.PlaceOrder, Exception> {
            // 1. 재고 차감
            productService.decreaseStocks(criteria.to())

            // 2. 상품 정보 조회 및 주문 생성
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
            }

            val placeCommand = OrderCommand.PlaceOrder(
                userId = criteria.userId,
                items = placeOrderItems,
            )

            val order = orderService.place(placeCommand)

            // 3. 주문 금액에서 쿠폰 할인 적용 (있는 경우)
            val orderAmount = order.totalAmount
            val couponDiscount = criteria.issuedCouponId?.let { issuedCouponId ->
                couponService.useCoupon(criteria.userId, issuedCouponId, orderAmount)
            } ?: Money.ZERO_KRW

            // 4. 포인트 차감 (0원 초과인 경우에만)
            if (criteria.usePoint > Money.ZERO_KRW) {
                pointService.deduct(criteria.userId, criteria.usePoint)
            }

            // 5. 결제 처리
            val payCommand = OrderCommand.Pay(
                orderId = order.id,
                userId = criteria.userId,
                usePoint = criteria.usePoint,
                issuedCouponId = criteria.issuedCouponId,
                couponDiscount = couponDiscount,
            )

            orderService.pay(payCommand)

            OrderInfo.PlaceOrder(order.id)
        }
    }
}
