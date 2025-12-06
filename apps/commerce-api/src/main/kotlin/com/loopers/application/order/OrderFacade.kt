package com.loopers.application.order

import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductView
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class OrderFacade(
    val productService: ProductService,
    val orderService: OrderService,
    val pointService: PointService,
    val couponService: CouponService,
    val paymentService: PaymentService,
    val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
    private val cacheTemplate: CacheTemplate,
    @Value("\${pg.callback-base-url}")
    private val callbackBaseUrl: String,
) {
    private lateinit var retryTemplate: RetryTemplate

    @PostConstruct
    fun init() {
        this.retryTemplate = RetryTemplate.builder()
            .maxAttempts(2)
            .uniformRandomBackoff(100, 500)
            .retryOn(ObjectOptimisticLockingFailureException::class.java)
            .build()
    }

    fun placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        return retryTemplate.execute<OrderInfo.PlaceOrder, Exception> {
            // 1. 리소스 할당 (재고, 쿠폰, 포인트, Payment 생성)
            val allocationResult = allocateResources(criteria)

            // 2. Payment 상태에 따라 분기 (Payment가 이미 paidAmount로 결정함)
            when {
                allocationResult.payment.status == PaymentStatus.PAID -> {
                    // 포인트+쿠폰 전액 결제 완료
                    updateProductCache(allocationResult.productViews)
                    OrderInfo.PlaceOrder(
                        orderId = allocationResult.orderId,
                        paymentId = allocationResult.payment.id,
                        paymentStatus = PaymentStatus.PAID,
                    )
                }

                !criteria.requiresCardPayment() -> {
                    // paidAmount > 0인데 카드 정보가 없음 → 포인트+쿠폰 부족
                    throw CoreException(
                        ErrorType.BAD_REQUEST,
                        "포인트와 쿠폰만으로 결제할 수 없습니다. 카드 정보를 입력해주세요.",
                    )
                }

                else -> {
                    // PG 결제 필요 (paidAmount > 0, 카드 정보 있음)
                    processCardPayment(criteria, allocationResult)
                }
            }
        }
    }

    /**
     * 카드 결제 처리
     * PG 호출 → 결과에 따라 상태 전이
     */
    private fun processCardPayment(
        criteria: OrderCriteria.PlaceOrder,
        allocationResult: ResourceAllocationResult,
    ): OrderInfo.PlaceOrder {
        // PG 호출 (트랜잭션 외부)
        val pgResult = requestPayment(criteria, allocationResult)

        // 결과 처리
        return when (pgResult) {
            is PgPaymentCreateResult.Accepted -> {
                handlePgSuccess(allocationResult, pgResult.transactionKey)
            }

            is PgPaymentCreateResult.Uncertain -> {
                // 타임아웃 등으로 응답을 받지 못한 경우
                // IN_PROGRESS로 전이하고 스케줄러가 나중에 처리
                // PENDING → IN_PROGRESS (transactionKey 없이)
                transactionTemplate.execute { _ ->
                    paymentService.initiatePayment(
                        paymentId = allocationResult.payment.id,
                        result = PgPaymentCreateResult.Uncertain,
                    )
                }

                updateProductCache(allocationResult.productViews)

                OrderInfo.PlaceOrder(
                    orderId = allocationResult.orderId,
                    paymentId = allocationResult.payment.id,
                    paymentStatus = PaymentStatus.IN_PROGRESS,
                )
            }
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
            val payment = paymentService.createPending(
                userId = criteria.userId,
                order = order,
                usedPoint = criteria.usePoint,
                issuedCouponId = criteria.issuedCouponId,
                couponDiscount = couponDiscount,
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
     * PG 결제 요청 (트랜잭션 외부)
     */
    private fun requestPayment(
        criteria: OrderCriteria.PlaceOrder,
        allocationResult: ResourceAllocationResult,
    ): PgPaymentCreateResult {
        val cardInfo = CardInfo(
            cardType = CardType.valueOf(criteria.cardType!!),
            cardNo = criteria.cardNo!!,
        )

        val request = PgPaymentRequest(
            orderId = allocationResult.orderId,
            amount = allocationResult.payment.paidAmount,
            cardInfo = cardInfo,
            callbackUrl = "$callbackBaseUrl/api/v1/payments/callback",
        )

        return pgClient.requestPayment(request)
    }

    /**
     * PG 성공 처리
     * - PENDING → IN_PROGRESS (initiate with Accepted)
     * - IN_PROGRESS → PAID (confirmPayment)
     */
    private fun handlePgSuccess(
        allocationResult: ResourceAllocationResult,
        transactionKey: String,
    ): OrderInfo.PlaceOrder {
        transactionTemplate.execute { _ ->
            // 1. 결제 개시 (PENDING → IN_PROGRESS, transactionKey 저장)
            paymentService.initiatePayment(
                paymentId = allocationResult.payment.id,
                result = PgPaymentCreateResult.Accepted(transactionKey),
            )

            // 2. 결제 확정 (IN_PROGRESS → PAID)
            val successTransaction = PgTransaction(
                transactionKey = transactionKey,
                orderId = allocationResult.payment.orderId,
                cardType = CardType.KB,
                cardNo = "0000-0000-0000-0000",
                amount = allocationResult.payment.paidAmount,
                status = PgTransactionStatus.SUCCESS,
            )
            val payment = paymentService.confirmPayment(
                paymentId = allocationResult.payment.id,
                transactions = listOf(successTransaction),
                currentTime = Instant.now(),
            )

            when (payment.status) {
                PaymentStatus.PAID -> orderService.completePayment(payment.orderId)
                PaymentStatus.FAILED -> {
                    recoverResources(payment)
                    orderService.cancelOrder(payment.orderId)
                }

                else -> {}
            }
        }

        updateProductCache(allocationResult.productViews)

        return OrderInfo.PlaceOrder(
            orderId = allocationResult.orderId,
            paymentId = allocationResult.payment.id,
            paymentStatus = PaymentStatus.PAID,
        )
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
     * 콜백/스케줄러에서 사용할 결제 결과 처리
     * 주의: 이 메서드는 이미 IN_PROGRESS 상태인 결제에 대해 호출됩니다.
     *
     * @param paymentId 결제 ID
     * @param transactions PG에서 조회한 트랜잭션 목록
     * @param currentTime 현재 시각 (타임아웃 판단용)
     */
    fun handlePaymentResult(
        paymentId: Long,
        transactions: List<PgTransaction>,
        currentTime: Instant,
    ) {
        val payment = paymentService.confirmPayment(paymentId, transactions, currentTime)
        if (payment.status == PaymentStatus.PAID) {
            orderService.completePayment(payment.orderId)
            return
        }

        recoverResources(payment)
        orderService.cancelOrder(payment.orderId)
    }

    /**
     * 리소스 복구 (포인트, 쿠폰, 재고)
     */
    private fun recoverResources(payment: Payment) {
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
