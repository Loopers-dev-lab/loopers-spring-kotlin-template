package com.loopers.application.order

import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderResult
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgService
import com.loopers.domain.payment.dto.PaymentCommand
import com.loopers.domain.payment.dto.PgCommand
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val couponService: CouponService,
    private val orderService: OrderService,
    private val userService: UserService,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val pointService: PointService,
    private val paymentService: PaymentService,
    private val pgService: PgService,
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

        // 6. 쿠폰 할인 금액 계산 (쿠폰 사용 중복 발생시 exception을 catch하여 예외를 던짐)
        val discountAmount = try {
            couponService.applyCoupon(user.id, couponId, totalAmount)
        } catch (e: ObjectOptimisticLockingFailureException) {
            log.debug("쿠폰 중복 사용 시도 무시: user=$user.id, couponId=$couponId")
            throw CoreException(ErrorType.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다")
        }

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

                // 10. PG 결제 요청
                val transactionKey = pgService.requestPayment(
                    command = PgCommand.Request(
                        userId = userId,
                        orderId = orderResult.order.id.toString(),
                        cardType = command.cardType,
                        cardNo = command.cardNo,
                        amount = finalAmount,
                    ),
                )

                // 11. transactionKey update
                paymentService.updateTransactionKey(payment.id, transactionKey)

                return
            }
        }

        // TODO. 주문 정보 외부 시스템 전송
    }
}
