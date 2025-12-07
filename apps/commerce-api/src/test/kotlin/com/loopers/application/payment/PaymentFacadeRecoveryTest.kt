package com.loopers.application.payment

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * 결제 복구 통합 테스트
 * - PG 실패 시 리소스 복구 (재고, 포인트, 쿠폰)
 * - 다양한 실패 시나리오에서의 복구 검증
 */
@SpringBootTest
@DisplayName("결제 복구 통합 테스트")
class PaymentFacadeRecoveryTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("processInProgressPayment")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("결제 실패 시 차감된 포인트가 복구된다")
        fun `point is restored when payment fails`() {
            // given
            val userId = 1L
            val initialBalance = Money.krw(100000)
            val pointAccount = createPointAccount(userId = userId, balance = initialBalance)
            val usedPoint = Money.krw(5000)

            val payment = createInProgressPaymentWithPoint(
                userId = userId,
                usedPoint = usedPoint,
            )

            val failedTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "PG 연결 실패",
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(payment.id)

            // then - 포인트가 복구됨
            val restoredAccount = pointAccountRepository.findByUserId(userId)!!

            assertAll(
                { assertThat(restoredAccount.balance).isEqualTo(initialBalance) },
                { assertThat(restoredAccount.balance).isEqualTo(pointAccount.balance) },
            )
        }

        @Test
        @DisplayName("포인트 사용 없이 결제 실패 시 포인트 복구가 수행되지 않는다")
        fun `no point restoration when no point was used`() {
            // given
            val userId = 1L
            val initialBalance = Money.krw(100000)
            createPointAccount(userId = userId, balance = initialBalance)

            val payment = createInProgressPaymentWithPoint(
                userId = userId,
                usedPoint = Money.ZERO_KRW,
            )

            val failedTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "카드 한도 초과",
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(payment.id)

            // then - 포인트 잔액 그대로
            val account = pointAccountRepository.findByUserId(userId)!!
            assertThat(account.balance).isEqualTo(initialBalance)
        }

        @Test
        @DisplayName("결제 실패 시 사용된 쿠폰이 복구된다")
        fun `coupon is restored when payment fails`() {
            // given
            val userId = 1L
            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            val payment = createInProgressPaymentWithCoupon(
                userId = userId,
                issuedCoupon = issuedCoupon,
            )

            val failedTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "서킷 브레이커 오픈",
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(payment.id)

            // then - 쿠폰 상태가 AVAILABLE로 복구됨
            val restoredCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!

            assertThat(restoredCoupon.status).isEqualTo(UsageStatus.AVAILABLE)
        }

        @Test
        @DisplayName("쿠폰 없이 결제 실패 시 쿠폰 복구가 수행되지 않는다")
        fun `no coupon restoration when no coupon was used`() {
            // given
            val userId = 1L
            val payment = createInProgressPaymentWithPoint(
                userId = userId,
                usedPoint = Money.krw(5000),
            )

            val failedTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "PG 거부",
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리 (쿠폰 없음)
            paymentFacade.processInProgressPayment(payment.id)

            // then - 결제가 실패됨 (쿠폰 관련 에러 없음)
            val failedPayment = paymentRepository.findById(payment.id)!!
            assertThat(failedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @Test
        @DisplayName("결제 실패 시 차감된 재고가 복구된다")
        fun `stock is restored when payment fails`() {
            // given
            val userId = 1L
            val initialStock = 100
            val orderQuantity = 5
            val product = createProduct(stock = Stock.of(initialStock))

            // 재고 차감 및 결제 생성
            val payment = createInProgressPaymentWithProduct(
                userId = userId,
                product = product,
                quantity = orderQuantity,
            )

            val failedTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "잔액 부족",
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(payment.id)

            // then - 재고가 복구됨
            val restoredProduct = productRepository.findById(product.id)!!
            assertThat(restoredProduct.stock.amount).isEqualTo(initialStock)
        }

        @Test
        @DisplayName("여러 상품의 재고가 모두 복구된다")
        fun `multiple product stocks are all restored when payment fails`() {
            // given
            val userId = 1L
            val product1 = createProduct(stock = Stock.of(100))
            val product2 = createProduct(stock = Stock.of(50))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            // 주문 생성 (재고 차감 시뮬레이션)
            val order = Order.place(userId)
            order.addOrderItem(product1.id, 5, "상품1", Money.krw(10000))
            order.addOrderItem(product2.id, 3, "상품2", Money.krw(15000))
            val savedOrder = orderRepository.save(order)

            // 재고 차감
            val updatedProduct1 = productRepository.findById(product1.id)!!
            updatedProduct1.decreaseStock(5)
            productRepository.save(updatedProduct1)

            val updatedProduct2 = productRepository.findById(product2.id)!!
            updatedProduct2.decreaseStock(3)
            productRepository.save(updatedProduct2)

            // Payment 생성 (usedPoint < totalAmount면 PENDING 상태)
            val payment = paymentService.createPending(
                userId = userId,
                order = savedOrder,
                usedPoint = Money.krw(50000),
            )

            // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
            payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
            val initiatedPayment = paymentRepository.save(payment)

            val failedTransaction = createTransaction(
                transactionKey = initiatedPayment.externalPaymentKey!!,
                paymentId = initiatedPayment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "타임아웃",
            )

            every { pgClient.findTransactionsByPaymentId(initiatedPayment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(initiatedPayment.id)

            // then - 모든 재고가 복구됨
            val restoredProduct1 = productRepository.findById(product1.id)!!
            val restoredProduct2 = productRepository.findById(product2.id)!!

            assertAll(
                { assertThat(restoredProduct1.stock.amount).isEqualTo(100) },
                { assertThat(restoredProduct2.stock.amount).isEqualTo(50) },
            )
        }

        @Test
        @DisplayName("결제 실패 시 주문 상태가 CANCELLED로 변경된다")
        fun `order status changes to CANCELLED when payment fails`() {
            // given
            val userId = 1L
            val payment = createInProgressPaymentWithPoint(
                userId = userId,
                usedPoint = Money.krw(5000),
            )

            val failedTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "결제 거부",
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(payment.id)

            // then - 주문 상태가 CANCELLED
            val cancelledOrder = orderRepository.findById(payment.orderId)!!
            assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @Test
        @DisplayName("결제 성공 시 주문 상태가 PAID로 변경된다")
        fun `order status changes to PAID when payment succeeds`() {
            // given
            val userId = 1L
            val payment = createInProgressPaymentWithPoint(
                userId = userId,
                usedPoint = Money.krw(5000),
            )
            val successTransaction = createTransaction(
                transactionKey = payment.externalPaymentKey!!,
                paymentId = payment.id,
                status = PgTransactionStatus.SUCCESS,
            )

            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(successTransaction)

            // when - 결제 성공 처리
            paymentFacade.processInProgressPayment(payment.id)

            // then - 주문 상태가 PAID
            val paidOrder = orderRepository.findById(payment.orderId)!!
            assertThat(paidOrder.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        @DisplayName("결제 실패 시 모든 리소스(포인트, 쿠폰, 재고)가 복구된다")
        fun `all resources are restored when payment fails`() {
            // given
            val userId = 1L
            val initialPoint = Money.krw(100000)
            val initialStock = 100
            val usedPoint = Money.krw(5000)
            val orderQuantity = 2

            createPointAccount(userId = userId, balance = initialPoint)
            val product = createProduct(stock = Stock.of(initialStock))
            val coupon = createCoupon(discountValue = 3000)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 주문 생성
            val order = Order.place(userId)
            order.addOrderItem(product.id, orderQuantity, "테스트 상품", Money.krw(10000))
            val savedOrder = orderRepository.save(order)

            // 재고 차감
            val updatedProduct = productRepository.findById(product.id)!!
            updatedProduct.decreaseStock(orderQuantity)
            productRepository.save(updatedProduct)

            // 포인트 차감
            val pointAccount = pointAccountRepository.findByUserId(userId)!!
            pointAccount.deduct(usedPoint)
            pointAccountRepository.save(pointAccount)

            // 쿠폰 사용 (시뮬레이션 - 직접 상태 변경)
            val couponToUse = issuedCouponRepository.findById(issuedCoupon.id)!!
            val couponDef = couponRepository.findById(coupon.id)!!
            couponToUse.use(userId, couponDef, java.time.ZonedDateTime.now())
            issuedCouponRepository.save(couponToUse)

            // Payment 생성 (couponDiscount 포함, paidAmount = 20000 - 5000 - 3000 = 12000 자동 계산)
            val payment = paymentService.createPending(
                userId = userId,
                order = savedOrder,
                usedPoint = usedPoint,
                issuedCouponId = issuedCoupon.id,
                couponDiscount = Money.krw(3000),
            )

            // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
            payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
            val initiatedPayment = paymentRepository.save(payment)

            val failedTransaction = createTransaction(
                transactionKey = initiatedPayment.externalPaymentKey!!,
                paymentId = initiatedPayment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "PG 서버 오류",
            )

            every { pgClient.findTransactionsByPaymentId(initiatedPayment.id) } returns listOf(failedTransaction)

            // when - 결제 실패 처리
            paymentFacade.processInProgressPayment(initiatedPayment.id)

            // then - 모든 리소스 복구
            val restoredPointAccount = pointAccountRepository.findByUserId(userId)!!
            val restoredProduct = productRepository.findById(product.id)!!
            val restoredCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            val cancelledOrder = orderRepository.findById(savedOrder.id)!!

            assertAll(
                { assertThat(restoredPointAccount.balance).isEqualTo(initialPoint) },
                { assertThat(restoredProduct.stock.amount).isEqualTo(initialStock) },
                { assertThat(restoredCoupon.status).isEqualTo(UsageStatus.AVAILABLE) },
                { assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED) },
            )
        }
    }

    // ===========================================
    // 헬퍼 메서드
    // ===========================================

    private fun createProduct(
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = "테스트 상품",
            price = price,
            stock = stock,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.ZERO_KRW,
    ): PointAccount {
        val account = PointAccount.of(userId, balance)
        return pointAccountRepository.save(account)
    }

    private fun createCoupon(
        name: String = "테스트 쿠폰",
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 5000,
    ): Coupon {
        val discountAmount = DiscountAmount(
            type = discountType,
            value = discountValue,
        )
        val coupon = Coupon.of(name = name, discountAmount = discountAmount)
        return couponRepository.save(coupon)
    }

    private fun createIssuedCoupon(
        userId: Long,
        coupon: Coupon,
    ): IssuedCoupon {
        val issuedCoupon = coupon.issue(userId)
        return issuedCouponRepository.save(issuedCoupon)
    }

    private fun createInProgressPaymentWithPoint(
        userId: Long = 1L,
        usedPoint: Money = Money.krw(5000),
    ): Payment {
        val product = createProduct(price = Money.krw(10000), stock = Stock.of(100))
        if (pointAccountRepository.findByUserId(userId) == null) {
            createPointAccount(userId = userId, balance = Money.krw(100000))
        }

        // Order 생성
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val savedOrder = orderRepository.save(order)

        // 포인트 차감 (시뮬레이션)
        if (usedPoint > Money.ZERO_KRW) {
            val pointAccount = pointAccountRepository.findByUserId(userId)!!
            pointAccount.deduct(usedPoint)
            pointAccountRepository.save(pointAccount)
        }

        // usedPoint < totalAmount면 PENDING 상태가 됨
        require(usedPoint < Money.krw(10000)) { "usedPoint must be < totalAmount for PENDING state" }

        // Payment 생성 (PENDING -> IN_PROGRESS), paidAmount = 10000 - usedPoint 자동 계산
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = usedPoint,
        )

        // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
        payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createInProgressPaymentWithCoupon(
        userId: Long = 1L,
        issuedCoupon: IssuedCoupon,
    ): Payment {
        val product = createProduct(price = Money.krw(10000), stock = Stock.of(100))
        createPointAccount(userId = userId, balance = Money.krw(100000))

        // Order 생성
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val savedOrder = orderRepository.save(order)

        // 쿠폰 사용 (시뮬레이션)
        val coupon = couponRepository.findById(issuedCoupon.couponId)!!
        issuedCoupon.use(userId, coupon, java.time.ZonedDateTime.now())
        issuedCouponRepository.save(issuedCoupon)

        val couponDiscount = Money.krw(5000)
        val usedPoint = Money.krw(3000)
        // paidAmount = 10000 - 3000 - 5000 = 2000 자동 계산

        // 포인트 차감 (시뮬레이션)
        val pointAccount = pointAccountRepository.findByUserId(userId)!!
        pointAccount.deduct(usedPoint)
        pointAccountRepository.save(pointAccount)

        // Payment 생성 (PENDING -> IN_PROGRESS)
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = usedPoint,
            issuedCouponId = issuedCoupon.id,
            couponDiscount = couponDiscount,
        )

        // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
        payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createInProgressPaymentWithProduct(
        userId: Long = 1L,
        product: Product,
        quantity: Int,
    ): Payment {
        createPointAccount(userId = userId, balance = Money.krw(100000))

        // Order 생성
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = quantity,
            productName = "테스트 상품",
            unitPrice = product.price,
        )
        val savedOrder = orderRepository.save(order)

        // 재고 차감 (시뮬레이션)
        product.decreaseStock(quantity)
        productRepository.save(product)

        val totalAmount = product.price * quantity
        val usedPoint = totalAmount / 2 // 절반은 포인트로, paidAmount = 나머지 자동 계산

        // 포인트 차감 (시뮬레이션)
        val pointAccount = pointAccountRepository.findByUserId(userId)!!
        pointAccount.deduct(usedPoint)
        pointAccountRepository.save(pointAccount)

        // Payment 생성 (PENDING -> IN_PROGRESS)
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = usedPoint,
        )

        // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
        payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createTransaction(
        transactionKey: String,
        paymentId: Long,
        status: PgTransactionStatus,
        failureReason: String? = null,
    ): PgTransaction {
        return PgTransaction(
            transactionKey = transactionKey,
            paymentId = paymentId,
            status = status,
            failureReason = failureReason,
        )
    }
}
