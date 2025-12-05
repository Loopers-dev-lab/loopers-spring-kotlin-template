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
import java.time.Instant
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
        @DisplayName("PG에서 SUCCESS 트랜잭션 조회 시 handlePaymentResult를 호출한다")
        fun `calls handlePaymentResult when PG returns SUCCESS`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)
            val successTransaction = createPgTransaction(
                transactionKey = "tx_123",
                orderId = 200L,
                status = PgTransactionStatus.SUCCESS,
            )

            every { pgClient.findTransactionsByOrderId(200L) } returns listOf(successTransaction)
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = listOf(successTransaction),
                    currentTime = any(),
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = listOf(successTransaction),
                    currentTime = any<Instant>(),
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG에서 FAILED 트랜잭션 조회 시 handlePaymentResult를 호출한다")
        fun `calls handlePaymentResult when PG returns FAILED`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)
            val failedTransaction = createPgTransaction(
                transactionKey = "tx_123",
                orderId = 200L,
                status = PgTransactionStatus.FAILED,
                failureReason = "잔액 부족",
            )

            every { pgClient.findTransactionsByOrderId(200L) } returns listOf(failedTransaction)
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = listOf(failedTransaction),
                    currentTime = any(),
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = listOf(failedTransaction),
                    currentTime = any<Instant>(),
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG에서 PENDING 트랜잭션 조회 시 handlePaymentResult를 호출한다 (confirmPayment에서 상태 결정)")
        fun `calls handlePaymentResult when PG returns PENDING`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(3),
            )
            val order = createMockOrder(orderId = 200L)
            val pendingTransaction = createPgTransaction(
                transactionKey = "tx_123",
                orderId = 200L,
                status = PgTransactionStatus.PENDING,
            )

            every { pgClient.findTransactionsByOrderId(200L) } returns listOf(pendingTransaction)
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = listOf(pendingTransaction),
                    currentTime = any(),
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = listOf(pendingTransaction),
                    currentTime = any<Instant>(),
                    orderItems = any(),
                )
            }
        }

        @Test
        @DisplayName("PG에 결제 기록이 없으면 빈 트랜잭션 목록으로 handlePaymentResult를 호출한다")
        fun `calls handlePaymentResult with empty list when no transaction found`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every { pgClient.findTransactionsByOrderId(200L) } returns emptyList()
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = emptyList(),
                    currentTime = any(),
                    orderItems = any(),
                )
            } just Runs

            // when
            facade.processInProgressPayment(payment)

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentResult(
                    paymentId = 1L,
                    transactions = emptyList<PgTransaction>(),
                    currentTime = any<Instant>(),
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
            verify(exactly = 0) {
                paymentResultHandler.handlePaymentResult(any(), any(), any(), any())
            }
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
