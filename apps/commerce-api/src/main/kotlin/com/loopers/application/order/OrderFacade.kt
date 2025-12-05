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
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductView
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
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
    val paymentResultHandler: PaymentResultHandler,
    private val transactionTemplate: TransactionTemplate,
    private val cacheTemplate: CacheTemplate,
    @Value("\${pg.callback-base-url}")
    private val callbackBaseUrl: String,
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
                        if (throwable is ObjectOptimisticLockingFailureException) {
                            logger.warn(
                                "낙관적 락 충돌 발생 - 재시도 횟수: {}",
                                context.retryCount,
                            )
                        }
                    }
                },
            )
            .build()
    }

    fun placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        return retryTemplate.execute<OrderInfo.PlaceOrder, Exception> {
            if (criteria.requiresCardPayment()) {
                placeOrderWithCardPayment(criteria)
            } else {
                placeOrderWithPointOnly(criteria)
            }
        }
    }

    /**
     * 포인트 전액 결제 (기존 방식)
     * 카드 결제 없이 포인트로만 결제하는 경우
     */
    private fun placeOrderWithPointOnly(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        val (orderInfo, productViews) = transactionTemplate.execute { _ ->
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

            // 6. 결제 처리 (즉시 PAID)
            val payCommand = OrderCommand.Pay(
                orderId = order.id,
                userId = criteria.userId,
                usePoint = criteria.usePoint,
                issuedCouponId = criteria.issuedCouponId,
                couponDiscount = couponDiscount,
            )

            val payment = orderService.pay(payCommand)

            // 포인트 전액 결제 검증: PAID 상태가 아니면 포인트+쿠폰이 부족한 것
            if (payment.status != PaymentStatus.PAID) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "포인트와 쿠폰만으로 결제할 수 없습니다. 카드 정보를 입력해주세요.",
                )
            }

            OrderInfo.PlaceOrder(
                orderId = order.id,
                paymentId = payment.id,
                paymentStatus = payment.status,
            ) to productViews
        }!!

        updateProductCache(productViews)
        return orderInfo
    }

    /**
     * 카드 결제 포함 주문 (새로운 방식)
     * TX1(리소스 할당) → PG 호출 → TX2(결과 처리)
     */
    private fun placeOrderWithCardPayment(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder {
        // TX1: 리소스 할당 및 PENDING 결제 생성
        val allocationResult = allocateResources(criteria)

        // PG 호출 (트랜잭션 외부)
        val pgResult = requestPayment(criteria, allocationResult)

        // TX2: 결과 처리
        return when (pgResult) {
            is PgCallResult.Success -> {
                handlePgSuccess(allocationResult, pgResult.transactionKey)
            }

            is PgCallResult.RequestNotReached -> {
                handlePgFailure(allocationResult, pgResult.reason)
            }

            is PgCallResult.ResponseUncertain -> {
                // 타임아웃 등으로 응답을 받지 못한 경우
                // IN_PROGRESS로 전이하고 스케줄러가 나중에 처리
                logger.warn(
                    "PG 응답 불확실 - paymentId: {}, reason: {}",
                    allocationResult.payment.id,
                    pgResult.reason,
                )

                // PENDING → IN_PROGRESS (transactionKey 없이)
                transactionTemplate.execute { _ ->
                    paymentService.initiatePayment(
                        paymentId = allocationResult.payment.id,
                        result = PgPaymentCreateResult.Uncertain,
                    )
                }

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
                orderItems = criteria.items.map {
                    PaymentResultHandler.OrderItemInfo(
                        productId = it.productId,
                        quantity = it.quantity,
                    )
                },
            )
        }!!
    }

    /**
     * PG 결제 요청 (트랜잭션 외부)
     */
    private fun requestPayment(
        criteria: OrderCriteria.PlaceOrder,
        allocationResult: ResourceAllocationResult,
    ): PgCallResult {
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

        return when (val result = pgClient.requestPayment(request)) {
            is PgPaymentCreateResult.Accepted -> PgCallResult.Success(result.transactionKey)
            is PgPaymentCreateResult.Uncertain -> PgCallResult.ResponseUncertain("PG 응답 불확실")
        }
    }

    /**
     * PG 성공 처리
     * - PENDING → IN_PROGRESS (initiate with Accepted)
     * - IN_PROGRESS → PAID (confirmPayment via handlePaymentResult)
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
            paymentResultHandler.handlePaymentResult(
                paymentId = allocationResult.payment.id,
                transactions = listOf(successTransaction),
                currentTime = Instant.now(),
                orderItems = allocationResult.orderItems,
            )
        }

        updateProductCache(allocationResult.productViews)

        return OrderInfo.PlaceOrder(
            orderId = allocationResult.orderId,
            paymentId = allocationResult.payment.id,
            paymentStatus = PaymentStatus.PAID,
        )
    }

    /**
     * PG 실패 처리 및 리소스 복구
     * - PENDING → IN_PROGRESS (initiate with Uncertain)
     * - IN_PROGRESS → FAILED (confirmPayment via handlePaymentResult)
     */
    private fun handlePgFailure(
        allocationResult: ResourceAllocationResult,
        reason: String,
    ): OrderInfo.PlaceOrder {
        transactionTemplate.execute { _ ->
            // 1. 결제 개시 (PENDING → IN_PROGRESS)
            paymentService.initiatePayment(
                paymentId = allocationResult.payment.id,
                result = PgPaymentCreateResult.Uncertain,
            )

            // 2. 결제 확정 (IN_PROGRESS → FAILED)
            val failedTransaction = PgTransaction(
                transactionKey = "failed_${allocationResult.payment.id}",
                orderId = allocationResult.payment.orderId,
                cardType = CardType.KB,
                cardNo = "0000-0000-0000-0000",
                amount = allocationResult.payment.paidAmount,
                status = PgTransactionStatus.FAILED,
                failureReason = reason,
            )
            paymentResultHandler.handlePaymentResult(
                paymentId = allocationResult.payment.id,
                transactions = listOf(failedTransaction),
                currentTime = Instant.now(),
                orderItems = allocationResult.orderItems,
            )
        }

        updateProductCache(allocationResult.productViews)

        throw CoreException(ErrorType.PAYMENT_FAILED, reason)
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
        val payment = paymentService.findById(paymentId)
        val order = orderService.findById(payment.orderId)
        val orderItems = order.orderItems.map {
            PaymentResultHandler.OrderItemInfo(
                productId = it.productId,
                quantity = it.quantity,
            )
        }
        paymentResultHandler.handlePaymentResult(
            paymentId = paymentId,
            transactions = transactions,
            currentTime = currentTime,
            orderItems = orderItems,
        )
    }

    /**
     * 리소스 할당 결과
     */
    private data class ResourceAllocationResult(
        val orderId: Long,
        val payment: Payment,
        val productViews: List<ProductView>,
        val orderItems: List<PaymentResultHandler.OrderItemInfo>,
    )

    /**
     * PG 호출 결과
     */
    private sealed class PgCallResult {
        data class Success(val transactionKey: String) : PgCallResult()
        data class RequestNotReached(val reason: String) : PgCallResult()
        data class ResponseUncertain(val reason: String) : PgCallResult()
    }
}
