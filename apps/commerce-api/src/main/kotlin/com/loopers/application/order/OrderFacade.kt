package com.loopers.application.order

import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductView
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class OrderFacade(
    val productService: ProductService,
    val orderService: OrderService,
    val pointService: PointService,
    val couponService: CouponService,
    val paymentService: PaymentService,
    private val transactionTemplate: TransactionTemplate,
    private val cacheTemplate: CacheTemplate,
    private val retryTemplate: RetryTemplate = RetryTemplate.builder()
        .maxAttempts(2)
        .uniformRandomBackoff(100, 500)
        .retryOn(ObjectOptimisticLockingFailureException::class.java)
        .build(),
) {
    fun placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        // 1. 리소스 할당 (with retry)
        val allocationResult = retryTemplate.execute<ResourceAllocationResult, Exception> {
            allocateResources(criteria)
        }
        val payment = allocationResult.payment
        updateProductCache(allocationResult.productViews)

        // 2. 포인트+쿠폰 전액 결제 완료된 경우
        if (payment.status == PaymentStatus.PAID) {
            return OrderInfo.PlaceOrder(
                orderId = allocationResult.orderId,
                paymentId = payment.id,
                paymentStatus = PaymentStatus.PAID,
            )
        }

        // 3. PG 결제 요청
        val cardInfo = CardInfo(
            cardType = CardType.valueOf(
                criteria.cardType
                    ?: throw CoreException(ErrorType.BAD_REQUEST, "카드 타입이 필요합니다."),
            ),
            cardNo = criteria.cardNo
                ?: throw CoreException(ErrorType.BAD_REQUEST, "카드 번호가 필요합니다."),
        )
        val updatedPayment = paymentService.requestPgPayment(payment.id, cardInfo)

        // 4. 상태에 따른 후속 처리
        return when (updatedPayment.status) {
            PaymentStatus.IN_PROGRESS -> {
                OrderInfo.PlaceOrder(
                    orderId = allocationResult.orderId,
                    paymentId = payment.id,
                    paymentStatus = PaymentStatus.IN_PROGRESS,
                )
            }

            PaymentStatus.FAILED -> {
                // 보상 트랜잭션 (with retry)
                retryTemplate.execute<Unit, Exception> {
                    recoverResources(updatedPayment)
                }
                throw CoreException(
                    ErrorType.INTERNAL_ERROR,
                    "결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.",
                )
            }

            else -> throw CoreException(ErrorType.INTERNAL_ERROR)
        }
    }

    /**
     * TX1: 리소스 할당
     * - 재고 차감
     * - 쿠폰 사용
     * - 포인트 차감
     * - PENDING 결제 생성
     * - 주문 생성
     */
    private fun allocateResources(criteria: OrderCriteria.PlaceOrder): ResourceAllocationResult {
        return transactionTemplate.execute { _ ->
            // 1. 재고 차감
            val productIds = criteria.items.map { it.productId }
            productService.decreaseStocks(criteria.to())

            // 2. 재고가 변경된 상품의 최신 정보 조회
            val productViews = productService.findAllProductViewByIds(productIds)

            // 3. 주문 생성을 위한 상품 맵 생성
            val productMap = productViews.map { it.product }.associateBy { it.id }

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

            // 4. 주문 금액에서 쿠폰 할인 적용 (있는 경우)
            val orderAmount = order.totalAmount
            val couponDiscount = criteria.issuedCouponId?.let { issuedCouponId ->
                couponService.useCoupon(criteria.userId, issuedCouponId, orderAmount)
            } ?: Money.ZERO_KRW

            // 5. 포인트 차감 (0원 초과인 경우에만)
            if (criteria.usePoint > Money.ZERO_KRW) {
                pointService.deduct(criteria.userId, criteria.usePoint)
            }

            // 6. PENDING 결제 생성 (paidAmount = totalAmount - usePoint - couponDiscount 자동 계산)
            val payment = paymentService.create(
                PaymentCommand.Create(
                    userId = criteria.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = criteria.usePoint,
                    issuedCouponId = criteria.issuedCouponId,
                    couponDiscount = couponDiscount,
                ),
            )

            // 결제는 PENDING 상태로 유지, PG 결과 수신 후 initiate()로 IN_PROGRESS 전이
            ResourceAllocationResult(
                orderId = order.id,
                payment = payment,
                productViews = productViews,
            )
        }!!
    }

    /**
     * 상품 캐시 업데이트
     */
    private fun updateProductCache(productViews: List<ProductView>) {
        val productCacheKeys = productViews
            .associateBy { ProductCacheKeys.ProductDetail(productId = it.product.id) }
        cacheTemplate.putAll(productCacheKeys)
    }

    /**
     * 리소스 복구 (포인트, 쿠폰, 재고)
     */
    private fun recoverResources(payment: Payment) {
        transactionTemplate.execute { _ ->
            if (payment.usedPoint > Money.ZERO_KRW) {
                pointService.restore(payment.userId, payment.usedPoint)
            }
            payment.issuedCouponId?.let { couponId ->
                couponService.cancelCouponUse(couponId)
            }
            val order = orderService.findById(payment.orderId)
            val increaseUnits = order.orderItems.map {
                ProductCommand.IncreaseStockUnit(productId = it.productId, amount = it.quantity)
            }
            productService.increaseStocks(ProductCommand.IncreaseStocks(units = increaseUnits))

            orderService.cancelOrder(payment.orderId)
        }
    }

    /**
     * 리소스 할당 결과
     */
    private data class ResourceAllocationResult(
        val orderId: Long,
        val payment: Payment,
        val productViews: List<ProductView>,
    )
}
