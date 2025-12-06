package com.loopers.application.payment

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.payment.PgRequestNotReachedException
import com.loopers.support.values.Money
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

@DisplayName("PaymentFacade 단위 테스트")
class PaymentFacadeTest {
    private lateinit var paymentService: PaymentService
    private lateinit var orderService: OrderService
    private lateinit var pointService: PointService
    private lateinit var couponService: CouponService
    private lateinit var productService: ProductService
    private lateinit var pgClient: PgClient
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var facade: PaymentFacade

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        paymentService = mockk()
        orderService = mockk()
        pointService = mockk()
        couponService = mockk()
        productService = mockk()
        pgClient = mockk()
        transactionTemplate = mockk()

        // TransactionTemplate mock - 실제로 콜백 실행
        every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
            val callback = firstArg<TransactionCallback<*>>()
            callback.doInTransaction(mockk())
        }

        facade = PaymentFacade(
            paymentService = paymentService,
            orderService = orderService,
            pointService = pointService,
            couponService = couponService,
            productService = productService,
            pgClient = pgClient,
            transactionTemplate = transactionTemplate,
        )
    }

    @Nested
    @DisplayName("findInProgressPayments")
    inner class FindInProgressPayments {

        @Test
        @DisplayName("PaymentService에 위임하여 IN_PROGRESS 결제 목록을 조회한다")
        fun `delegates to PaymentService`() {
            // given
            val threshold = ZonedDateTime.now().minusMinutes(1)
            val payments = listOf(createMockPayment(id = 1L))
            every { paymentService.findInProgressPayments(threshold) } returns payments

            // when
            val result = facade.findInProgressPayments(threshold)

            // then
            verify(exactly = 1) { paymentService.findInProgressPayments(threshold) }
            assert(result == payments)
        }
    }

    @Nested
    @DisplayName("processInProgressPayment - PG 결과에 따른 처리")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("PG에서 SUCCESS 트랜잭션 조회 시 confirmPayment와 completePayment를 호출한다")
        fun `calls confirmPayment and completePayment when PG returns SUCCESS`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val confirmedPayment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.PAID,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val successTransaction = createPgTransaction(
                transactionKey = "tx_123",
                orderId = 200L,
                status = PgTransactionStatus.SUCCESS,
            )

            every { paymentService.findById(paymentId) } returns payment
            every { pgClient.findTransactionsByOrderId(200L) } returns listOf(successTransaction)
            every {
                paymentService.confirmPayment(paymentId, listOf(successTransaction), any())
            } returns confirmedPayment
            every { orderService.completePayment(200L) } returns mockk()

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.confirmPayment(paymentId, listOf(successTransaction), any()) }
            verify(exactly = 1) { orderService.completePayment(200L) }
        }

        @Test
        @DisplayName("PG에서 FAILED 트랜잭션 조회 시 confirmPayment와 cancelOrder를 호출한다")
        fun `calls confirmPayment and cancelOrder when PG returns FAILED`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val confirmedPayment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.FAILED,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)
            val failedTransaction = createPgTransaction(
                transactionKey = "tx_123",
                orderId = 200L,
                status = PgTransactionStatus.FAILED,
                failureReason = "잔액 부족",
            )

            every { paymentService.findById(paymentId) } returns payment
            every { pgClient.findTransactionsByOrderId(200L) } returns listOf(failedTransaction)
            every {
                paymentService.confirmPayment(paymentId, listOf(failedTransaction), any())
            } returns confirmedPayment
            every { orderService.findById(200L) } returns order
            every { productService.increaseStocks(any()) } just Runs
            every { orderService.cancelOrder(200L) } returns mockk()

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.confirmPayment(paymentId, listOf(failedTransaction), any()) }
            verify(exactly = 1) { orderService.cancelOrder(200L) }
        }

        @Test
        @DisplayName("PG에서 PENDING 트랜잭션 조회 시 confirmPayment를 호출한다 (상태 유지)")
        fun `calls confirmPayment when PG returns PENDING`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(3),
            )
            val confirmedPayment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(3),
            )
            val pendingTransaction = createPgTransaction(
                transactionKey = "tx_123",
                orderId = 200L,
                status = PgTransactionStatus.PENDING,
            )

            every { paymentService.findById(paymentId) } returns payment
            every { pgClient.findTransactionsByOrderId(200L) } returns listOf(pendingTransaction)
            every {
                paymentService.confirmPayment(paymentId, listOf(pendingTransaction), any())
            } returns confirmedPayment

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.confirmPayment(paymentId, listOf(pendingTransaction), any()) }
            verify(exactly = 0) { orderService.completePayment(any()) }
            verify(exactly = 0) { orderService.cancelOrder(any()) }
        }

        @Test
        @DisplayName("PG에 결제 기록이 없으면 빈 트랜잭션 목록으로 confirmPayment를 호출한다")
        fun `calls confirmPayment with empty list when no transaction found`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val confirmedPayment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every { paymentService.findById(paymentId) } returns payment
            every { pgClient.findTransactionsByOrderId(200L) } returns emptyList()
            every {
                paymentService.confirmPayment(paymentId, emptyList(), any())
            } returns confirmedPayment

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.confirmPayment(paymentId, emptyList(), any()) }
        }

        @Test
        @DisplayName("PG 조회 실패 시 예외를 던진다")
        fun `throws exception when PG query fails`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every { paymentService.findById(paymentId) } returns payment
            every {
                pgClient.findTransactionsByOrderId(200L)
            } throws PgRequestNotReachedException("PG 연결 실패")

            // when & then
            org.junit.jupiter.api.assertThrows<PgRequestNotReachedException> {
                facade.processInProgressPayment(paymentId)
            }
            verify(exactly = 0) { paymentService.confirmPayment(any(), any(), any()) }
        }
    }

    private fun createMockPayment(
        id: Long = 1L,
        userId: Long = 100L,
        orderId: Long = 200L,
        status: PaymentStatus = PaymentStatus.IN_PROGRESS,
        createdAt: ZonedDateTime = ZonedDateTime.now().minusMinutes(2),
        externalPaymentKey: String? = "tx_mock_$id",
    ): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns id
        every { payment.userId } returns userId
        every { payment.orderId } returns orderId
        every { payment.status } returns status
        every { payment.createdAt } returns createdAt
        every { payment.usedPoint } returns Money.krw(0)
        every { payment.issuedCouponId } returns null
        every { payment.externalPaymentKey } returns externalPaymentKey
        return payment
    }

    private fun createMockOrder(orderId: Long): Order {
        val orderItem = mockk<OrderItem>()
        every { orderItem.productId } returns 1L
        every { orderItem.quantity } returns 1

        val order = mockk<Order>()
        every { order.id } returns orderId
        every { order.orderItems } returns mutableListOf(orderItem)
        return order
    }

    private fun createPgTransaction(
        transactionKey: String,
        orderId: Long,
        status: PgTransactionStatus,
        failureReason: String? = null,
    ): PgTransaction {
        return PgTransaction(
            transactionKey = transactionKey,
            orderId = orderId,
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9012-3456",
            amount = Money.krw(10000),
            status = status,
            failureReason = failureReason,
        )
    }
}
