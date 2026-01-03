package com.loopers.application.order

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentRequest
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.event.UserActionEvent
import com.loopers.domain.event.UserActionType
import com.loopers.domain.order.Money
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderQueryService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductQueryService
import com.loopers.domain.product.StockService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val orderQueryService: OrderQueryService,
    private val productQueryService: ProductQueryService,
    private val stockService: StockService,
    private val pointService: PointService,
    private val couponService: CouponService,
    private val paymentFacade: PaymentFacade,
    private val eventPublisher: ApplicationEventPublisher,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(OrderFacade::class.java)
    }

    @Transactional
    fun createOrder(userId: Long, request: OrderCreateRequest): OrderCreateInfo {
        val orderItems = validateAndCreateOrderItems(request)
        val totalMoney = calculateTotalAmount(orderItems)

        // 쿠폰 할인 계산 (실제 사용은 이벤트로 처리)
        val discountAmount = calculateCouponDiscount(userId, request.couponId, totalMoney)
        val finalAmount = totalMoney - discountAmount

        // 결제 방식에 따라 처리 순서 다름
        val paymentMethod = try {
            PaymentMethod.valueOf(request.paymentMethod)
        } catch (e: IllegalArgumentException) {
            throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 결제 방식입니다: ${request.paymentMethod}")
        }
        when (paymentMethod) {
            PaymentMethod.POINT -> {
                // 포인트 결제: 포인트 검증 → 주문 생성 → 재고 차감 → 포인트 차감
                pointService.validateUserPoint(userId, finalAmount)

                val order = orderService.createOrder(userId, orderItems)
                deductStocks(request.items)
                pointService.deductPoint(userId, finalAmount)

                logger.info("포인트 결제 완료: orderId=${order.id}, amount=${finalAmount.amount}")

                publishOrderEvents(order, userId, request.couponId, paymentMethod, finalAmount)

                return OrderCreateInfo.from(order)
            }

            PaymentMethod.CARD -> {
                // 카드 결제: 재고 차감 → 주문 생성 → PG 결제 요청
                if (request.cardType == null || request.cardNo == null) {
                    throw CoreException(ErrorType.BAD_REQUEST, "카드 결제 시 카드 정보는 필수입니다.")
                }

                deductStocks(request.items)
                val order = orderService.createOrder(userId, orderItems)

                val paymentRequest = PaymentRequest(
                    userId = userId,
                    orderId = requireNotNull(order.id) { "Order id must not be null after creation" },
                    amount = finalAmount.amount.toLong(),
                    cardType = request.cardType,
                    cardNo = request.cardNo,
                )

                try {
                    val paymentInfo = paymentFacade.requestCardPayment(paymentRequest)
                    logger.info("카드 결제 요청 완료: orderId=${order.id}, transactionKey=${paymentInfo.transactionKey}")

                    publishOrderEvents(order, userId, request.couponId, paymentMethod, finalAmount)
                } catch (e: Exception) {
                    logger.error("카드 결제 요청 실패: orderId=${order.id}", e)

                    // 별도 트랜잭션으로 주문 취소 상태 저장 (REQUIRES_NEW)
                    // 재고는 메인 트랜잭션 롤백으로 자동 복구됨
                    orderService.cancelOrderForPaymentFailure(order)

                    throw CoreException(ErrorType.INTERNAL_ERROR, "결제 처리에 실패했습니다: ${e.message}")
                }

                return OrderCreateInfo.from(order)
            }

            PaymentMethod.MIXED -> {
                throw CoreException(ErrorType.BAD_REQUEST, "혼합 결제는 아직 지원하지 않습니다.")
            }
        }
    }

    /**
     * 주문 생성 관련 이벤트를 발행합니다.
     * OrderCreatedEvent와 UserActionEvent를 발행하여 주문 생성을 알립니다.
     */
    private fun publishOrderEvents(
        order: com.loopers.domain.order.Order,
        userId: Long,
        couponId: Long?,
        paymentMethod: PaymentMethod,
        finalAmount: Money,
    ) {
        // 주문 생성 이벤트 발행
        eventPublisher.publishEvent(OrderCreatedEvent.from(order, couponId))

        // 유저 행동 로깅
        eventPublisher.publishEvent(
            UserActionEvent(
                userId = userId,
                actionType = UserActionType.ORDER_CREATE,
                targetType = "ORDER",
                targetId = order.id,
                metadata = mapOf(
                    "paymentMethod" to paymentMethod.name,
                    "amount" to finalAmount.amount.toLong(),
                ),
            ),
        )
    }

    /**
     * 쿠폰 할인 금액 계산
     * 쿠폰 유효성을 검증하고 할인 금액을 계산합니다.
     * 실제 쿠폰 사용 처리(상태 변경)는 OrderCreatedEvent 핸들러에서 비동기로 수행됩니다.
     */
    private fun calculateCouponDiscount(userId: Long, couponId: Long?, totalAmount: Money): Money {
        if (couponId == null) {
            return Money(BigDecimal.ZERO, totalAmount.currency)
        }

        // 쿠폰 정보 조회 및 사용 가능 여부 검증
        val userCoupon = couponService.getUserCoupon(userId, couponId)

        // 사용 가능 여부 검증
        if (!userCoupon.canUse()) {
            throw CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다")
        }

        return userCoupon.coupon.calculateDiscount(totalAmount)
    }

    private fun validateAndCreateOrderItems(
        request: OrderCreateRequest,
    ): List<OrderItem> = request.items.map { item ->
        val productDetail = productQueryService.getProductDetail(item.productId)
        val product = productDetail.product

        stockService.validateStockAvailability(productDetail.stock, product.name, item.quantity)

        createOrderItemSnapshot(product, item.quantity)
    }

    private fun calculateTotalAmount(orderItems: List<OrderItem>): Money {
        if (orderItems.isEmpty()) {
            return Money(BigDecimal.ZERO, com.loopers.domain.product.Currency.KRW)
        }

        val firstCurrency = orderItems.first().priceAtOrder.currency
        if (orderItems.any { it.priceAtOrder.currency != firstCurrency }) {
            throw CoreException(ErrorType.BAD_REQUEST, "모든 주문 항목은 동일한 통화를 사용해야 합니다.")
        }

        val totalAmount = orderItems.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.calculateItemAmount().amount
        }

        return Money(amount = totalAmount, currency = firstCurrency)
    }

    private fun deductStocks(items: List<OrderItemRequest>) {
        items.sortedBy { it.productId }.forEach { item ->
            stockService.decreaseStock(item.productId, item.quantity)
        }
    }

    private fun createOrderItemSnapshot(
        product: Product,
        quantity: Int,
    ): OrderItem = OrderItem(
        productId = product.id,
        productName = product.name,
        brandId = product.brand.id,
        brandName = product.brand.name,
        brandDescription = product.brand.description,
        quantity = quantity,
        priceAtOrder = product.price,
    )

    fun getOrders(
        userId: Long,
        pageable: Pageable,
    ): Page<OrderListInfo> = orderQueryService
        .getOrders(userId, pageable)
        .map { OrderListInfo.from(it) }

    fun getOrderDetail(
        userId: Long,
        orderId: Long,
    ): OrderDetailInfo = OrderDetailInfo.from(
        orderQueryService.getOrderDetail(userId, orderId),
    )
}
