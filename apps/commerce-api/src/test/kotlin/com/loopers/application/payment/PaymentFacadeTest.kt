package com.loopers.application.payment

import com.loopers.application.order.PaymentResultHandler
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
import com.loopers.infrastructure.pg.PgException
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
    private lateinit var paymentResultHandler: PaymentResultHandler
    private lateinit var pgClient: PgClient
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var facade: PaymentFacade

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        paymentService = mockk()
        orderService = mockk()
        paymentResultHandler = mockk()
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
            paymentResultHandler = paymentResultHandler,
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
        @DisplayName("PG에서 SUCCESS 응답 시 결제 성공 처리를 호출한다")
        fun `calls handlePaymentSuccess when PG returns SUCCESS`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every {
                pgClient.findTransactionsByOrderId(200L)
            } returns listOf(
                createPgTransaction(
                    transactionKey = "tx_123",
                    orderId = 200L,
                    status = PgTransactionStatus.SUCCESS,
                ),
            )
            every { paymentResultHandler.handlePaymentSuccess(1L, "tx_123") } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) { paymentResultHandler.handlePaymentSuccess(1L, "tx_123") }
        }

        @Test
        @DisplayName("PG에서 FAILED 응답 시 결제 실패 처리를 호출한다")
        fun `calls handlePaymentFailure when PG returns FAILED`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every {
                pgClient.findTransactionsByOrderId(200L)
            } returns listOf(
                createPgTransaction(
                    transactionKey = "tx_123",
                    orderId = 200L,
                    status = PgTransactionStatus.FAILED,
                    failureReason = "잔액 부족",
                ),
            )
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "잔액 부족",
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "잔액 부족",
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG PENDING이고 5분 이하이면 아무 처리도 하지 않는다")
        fun `does nothing when PG returns PENDING and under 5 minutes`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(3),
            )

            every {
                pgClient.findTransactionsByOrderId(200L)
            } returns listOf(
                createPgTransaction(
                    transactionKey = "tx_123",
                    orderId = 200L,
                    status = PgTransactionStatus.PENDING,
                ),
            )

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 0) { paymentResultHandler.handlePaymentSuccess(any(), any()) }
            verify(exactly = 0) { paymentResultHandler.handlePaymentFailure(any(), any(), any()) }
        }

        @Test
        @DisplayName("PG PENDING이고 5분 초과이면 강제 실패 처리한다")
        fun `forces failure when PG returns PENDING and over 5 minutes`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(6),
            )
            val order = createMockOrder(orderId = 200L)

            every {
                pgClient.findTransactionsByOrderId(200L)
            } returns listOf(
                createPgTransaction(
                    transactionKey = "tx_123",
                    orderId = 200L,
                    status = PgTransactionStatus.PENDING,
                ),
            )
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "결제 시간 초과 (5분)",
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "결제 시간 초과 (5분)",
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG에 결제 기록이 없으면 실패 처리한다")
        fun `fails payment when no transaction found in PG`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every {
                pgClient.findTransactionsByOrderId(200L)
            } returns emptyList()
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "PG에 결제 기록 없음",
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "PG에 결제 기록 없음",
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG NOT_FOUND 에러 시 실패 처리한다")
        fun `fails payment when PG returns NOT_FOUND error`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every {
                pgClient.findTransactionsByOrderId(200L)
            } throws PgException.BusinessError("NOT_FOUND", "결제 정보 없음")
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "PG에 결제 기록 없음",
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "PG에 결제 기록 없음",
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG 조회 실패 시 아무 처리도 하지 않는다 (다음 스케줄에 재시도)")
        fun `does nothing when PG query fails`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every {
                pgClient.findTransactionsByOrderId(200L)
            } throws PgException.RequestNotReached("PG 연결 실패")

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 0) { paymentResultHandler.handlePaymentSuccess(any(), any()) }
            verify(exactly = 0) { paymentResultHandler.handlePaymentFailure(any(), any(), any()) }
        }
    }

    private fun createMockPayment(
        id: Long = 1L,
        userId: Long = 100L,
        orderId: Long = 200L,
        status: PaymentStatus = PaymentStatus.IN_PROGRESS,
        createdAt: ZonedDateTime = ZonedDateTime.now().minusMinutes(2),
    ): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns id
        every { payment.userId } returns userId
        every { payment.orderId } returns orderId
        every { payment.status } returns status
        every { payment.createdAt } returns createdAt
        every { payment.usedPoint } returns Money.krw(0)
        every { payment.issuedCouponId } returns null
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
