package com.loopers.application.order

import com.loopers.application.order.event.OrderEvent
import com.loopers.application.payment.event.PaymentEvent
import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderResult
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.dto.PaymentCommand
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 파사드
 *
 * 책임:
 * - 주문 생성 비즈니스 로직 조율
 * - 결제 수단별 처리 흐름 분기 (포인트/카드)
 * - 도메인 서비스 호출 및 이벤트 발행
 *
 * 결제 수단별 처리 흐름:
 *
 * 1. POINT 결제:
 *    - 포인트 사용 → 주문 생성 → 재고 차감 → 이벤트 발행
 *    - 모든 처리가 하나의 트랜잭션에서 완료
 *    - OrderCreated 이벤트 → 쿠폰 사용 (BEFORE_COMMIT)
 *    - OrderCompleted 이벤트 → 데이터 플랫폼 전송 (AFTER_COMMIT)
 *
 * 2. CARD 결제:
 *    - 주문 생성 → 결제 생성 → 이벤트 발행
 *    - 재고 차감은 PG 결제 성공 후 PaymentEventListener에서 처리
 *    - PaymentCreated 이벤트 → PG API 요청 (AFTER_COMMIT)
 *    - OrderCreated 이벤트 → 쿠폰 사용 (BEFORE_COMMIT), 로깅 (AFTER_COMMIT)
 */
@Component
class OrderFacade(
    private val couponService: CouponService,
    private val orderService: OrderService,
    private val userService: UserService,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val pointService: PointService,
    private val paymentService: PaymentService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(OrderFacade::class.java)

    @Transactional(readOnly = true)
    fun getOrders(userId: String, pageable: Pageable): Page<OrderResult.ListInfo> {
        val user = userService.getMyInfo(userId)

        val orderPage = orderService.getOrders(user.id, pageable)

        return orderPage.map { OrderResult.ListInfo.from(it) }
    }

    @Transactional(readOnly = true)
    fun getOrder(userId: String, orderId: Long): OrderResult.DetailInfo {
        val user = userService.getMyInfo(userId)

        val order = orderService.getOrder(user.id, orderId)
        val orderDetail = orderService.getOrderDetail(orderId)

        return OrderResult.DetailInfo.from(order, orderDetail)
    }

    @Transactional
    fun placeOrder(command: OrderCommand.Place) {
        val userId = command.userId
        val couponId = command.couponId
        val items = command.items
        val paymentMethod = command.paymentMethod

        val user = userService.getMyInfo(userId)

        // 1. 상품 조회
        val productIds = items.map { it.productId }
        val products = productService.getProducts(productIds)

        // 2. 상품 존재 여부 검증
        productService.validateProductsExist(items, products)

        // 3. 재고 충분성 검증
        productService.validateStockAvailability(items)

        // 4. 브랜드 조회
        val brandIds = products.map { it.brandId }.distinct()
        val brands = brandService.getAllBrand(brandIds)

        // 5. 총 주문 금액 계산
        val totalAmount = orderService.calculateTotalAmount(items, products)

        // 6. 쿠폰 적용시 할인 금액 계산 (쿠폰 사용 처리는 이벤트로 분리)
        val discountAmount = couponService.calculateCouponDiscount(user.id, couponId, totalAmount)

        // 7. 최종 결제 금액 계산
        val finalAmount = totalAmount - discountAmount

        // TODO: 전략 패턴 적용
        when (paymentMethod) {
            PaymentMethod.POINT -> {
                // 8. 포인트 사용
                pointService.use(
                    userId = user.id,
                    amount = finalAmount,
                )

                // 9. 주문 생성
                val orderResult = orderService.createOrder(
                    OrderCommand.Create(
                        userId = user.id,
                        totalAmount = finalAmount,
                        items = items,
                        brands = brands,
                        products = products,
                        couponId = couponId,
                        status = OrderStatus.PENDING,
                    ),
                )

                // 10. 재고 감소
                productService.deductAllStock(orderResult.orderDetails)

                // 12. 주문 생성 이벤트 발행 (쿠폰 사용 후속 처리)
                applicationEventPublisher.publishEvent(
                    OrderEvent.OrderCreated(
                        orderId = orderResult.order.id,
                        userId = user.id,
                        couponId = couponId,
                    ),
                )

                // 11. 주문 완료 이벤트 발행 (데이터 플랫폼 전송, 사용자 활동 로깅)
                applicationEventPublisher.publishEvent(
                    OrderEvent.OrderCompleted(
                        orderId = orderResult.order.id,
                        userId = user.id,
                        totalAmount = finalAmount,
                        items = orderResult.orderDetails,
                    ),
                )
                return
            }

            PaymentMethod.CARD -> { // 재고 차감은 성공 콜백에서 진행
                requireNotNull(command.cardType) { "카드 결제 시 cardType은 필수입니다" }
                requireNotNull(command.cardNo) { "카드 결제 시 cardNo는 필수입니다" }

                // 8. 주문 생성
                val orderResult = orderService.createOrder(
                    OrderCommand.Create(
                        userId = user.id,
                        totalAmount = finalAmount,
                        items = items,
                        brands = brands,
                        products = products,
                        couponId = couponId,
                        status = OrderStatus.PENDING,
                    ),
                )

                // 9. Payment(PENDING) 생성
                val payment = paymentService.create(
                    PaymentCommand.Create(
                        userId = user.id,
                        orderId = orderResult.order.id,
                        cardType = command.cardType,
                        cardNo = command.cardNo,
                        amount = finalAmount,
                    ),
                )

                // 10. PG 결제 요청 이벤트 발행 (AFTER_COMMIT에서 처리)
                applicationEventPublisher.publishEvent(
                    PaymentEvent.PaymentCreated(
                        paymentId = payment.id,
                        orderId = orderResult.order.id.toString(),
                        userId = userId,
                        cardType = command.cardType,
                        cardNo = command.cardNo,
                        amount = finalAmount,
                    ),
                )

                // 11. 주문 생성 이벤트 발행 (쿠폰 사용 후속 처리(before), 사용자 활동 로깅(after)
                applicationEventPublisher.publishEvent(
                    OrderEvent.OrderCreated(
                        orderId = orderResult.order.id,
                        userId = user.id,
                        couponId = couponId,
                    ),
                )
                return
            }
        }
    }
}
