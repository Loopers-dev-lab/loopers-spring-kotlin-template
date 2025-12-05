package com.loopers.infrastructure.payment

import com.loopers.application.order.PaymentResultHandler
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentService
import com.loopers.domain.order.PaymentStatus
import com.loopers.infrastructure.pg.PgClient
import com.loopers.infrastructure.pg.PgException
import com.loopers.infrastructure.pg.PgPaymentListResponse
import com.loopers.infrastructure.pg.PgTransactionStatus
import com.loopers.infrastructure.pg.PgTransactionSummary
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
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

@DisplayName("PaymentStatusScheduler 단위 테스트")
class PaymentStatusSchedulerTest {
    private lateinit var paymentService: PaymentService
    private lateinit var orderService: OrderService
    private lateinit var paymentResultHandler: PaymentResultHandler
    private lateinit var pgClient: PgClient
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var scheduler: PaymentStatusScheduler

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

        scheduler = PaymentStatusScheduler(
            paymentService = paymentService,
            orderService = orderService,
            paymentResultHandler = paymentResultHandler,
            pgClient = pgClient,
            transactionTemplate = transactionTemplate,
        )
    }

    @Nested
    @DisplayName("IN_PROGRESS 결제 처리")
    inner class ProcessInProgressPayments {

        @Test
        @DisplayName("PG에서 SUCCESS 응답 시 결제 성공 처리를 호출한다")
        fun `calls handlePaymentSuccess when PG returns SUCCESS`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every { paymentService.findInProgressPayments(any()) } returns listOf(payment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } returns PgPaymentListResponse(
                orderId = "200",
                transactions = listOf(
                    PgTransactionSummary(
                        transactionKey = "tx_123",
                        status = PgTransactionStatus.SUCCESS.name,
                        reason = null,
                    ),
                ),
            )
            every { paymentResultHandler.handlePaymentSuccess(1L, "tx_123") } just Runs

            // when
            scheduler.checkInProgressPayments()

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
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every { paymentService.findInProgressPayments(any()) } returns listOf(payment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } returns PgPaymentListResponse(
                orderId = "200",
                transactions = listOf(
                    PgTransactionSummary(
                        transactionKey = "tx_123",
                        status = PgTransactionStatus.FAILED.name,
                        reason = "잔액 부족",
                    ),
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
            scheduler.checkInProgressPayments()

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
        @DisplayName("IN_PROGRESS 결제가 없으면 아무 작업도 하지 않는다")
        fun `does nothing when no IN_PROGRESS payments`() {
            // given
            every { paymentService.findInProgressPayments(any()) } returns emptyList()

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 0) { pgClient.getPaymentsByOrderId(any(), any()) }
        }
    }

    @Nested
    @DisplayName("낙관적 락 충돌 처리")
    inner class OptimisticLockConflict {

        @Test
        @DisplayName("낙관적 락 충돌 시 해당 결제를 건너뛴다")
        fun `skips payment when optimistic lock conflict occurs`() {
            // given
            val payment1 = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val payment2 = createMockPayment(
                id = 2L,
                userId = 101L,
                orderId = 201L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every { paymentService.findInProgressPayments(any()) } returns listOf(payment1, payment2)

            // payment1 - 낙관적 락 충돌
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } returns PgPaymentListResponse(
                orderId = "200",
                transactions = listOf(
                    PgTransactionSummary(
                        transactionKey = "tx_123",
                        status = PgTransactionStatus.SUCCESS.name,
                        reason = null,
                    ),
                ),
            )

            // 첫 번째 트랜잭션에서 낙관적 락 예외 발생
            var callCount = 0
            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                callCount++
                if (callCount == 1) {
                    throw ObjectOptimisticLockingFailureException(Payment::class.java, payment1.id)
                }
                val callback = firstArg<TransactionCallback<*>>()
                callback.doInTransaction(mockk())
            }

            // payment2 - 정상 처리
            every {
                pgClient.getPaymentsByOrderId(101L, "201")
            } returns PgPaymentListResponse(
                orderId = "201",
                transactions = listOf(
                    PgTransactionSummary(
                        transactionKey = "tx_456",
                        status = PgTransactionStatus.SUCCESS.name,
                        reason = null,
                    ),
                ),
            )
            every { paymentResultHandler.handlePaymentSuccess(2L, "tx_456") } just Runs

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 0) { paymentResultHandler.handlePaymentSuccess(1L, any()) }
            verify(exactly = 1) { paymentResultHandler.handlePaymentSuccess(2L, "tx_456") }
        }
    }

    @Nested
    @DisplayName("5분 초과 강제 실패")
    inner class ForceFailure {

        @Test
        @DisplayName("5분 초과 시 강제 실패 처리한다")
        fun `forces failure for payments older than 5 minutes`() {
            // given
            // 6분 전 생성된 결제
            val oldPayment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(6),
            )
            val order = createMockOrder(orderId = 200L)

            every { paymentService.findInProgressPayments(any()) } returns listOf(oldPayment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } returns PgPaymentListResponse(
                orderId = "200",
                transactions = listOf(
                    PgTransactionSummary(
                        transactionKey = "tx_123",
                        status = PgTransactionStatus.PENDING.name,
                        reason = null,
                    ),
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
            scheduler.checkInProgressPayments()

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
        @DisplayName("5분 이하이고 PG가 PENDING이면 다음 스케줄에 재시도한다")
        fun `waits for next schedule when payment is pending and under 5 minutes`() {
            // given
            // 3분 전 생성된 결제
            val recentPayment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(3),
            )

            every { paymentService.findInProgressPayments(any()) } returns listOf(recentPayment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } returns PgPaymentListResponse(
                orderId = "200",
                transactions = listOf(
                    PgTransactionSummary(
                        transactionKey = "tx_123",
                        status = PgTransactionStatus.PENDING.name,
                        reason = null,
                    ),
                ),
            )

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 0) { paymentResultHandler.handlePaymentSuccess(any(), any()) }
            verify(exactly = 0) { paymentResultHandler.handlePaymentFailure(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("PG 조회 실패 처리")
    inner class PgQueryFailure {

        @Test
        @DisplayName("PG 조회 실패 시 다음 스케줄에 재시도한다")
        fun `waits for next schedule when PG query fails`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )

            every { paymentService.findInProgressPayments(any()) } returns listOf(payment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } throws PgException.RequestNotReached("PG 연결 실패")

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 0) { paymentResultHandler.handlePaymentSuccess(any(), any()) }
            verify(exactly = 0) { paymentResultHandler.handlePaymentFailure(any(), any(), any()) }
        }

        @Test
        @DisplayName("PG에 결제 기록이 없으면 실패 처리한다")
        fun `fails payment when no transaction found in PG`() {
            // given
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every { paymentService.findInProgressPayments(any()) } returns listOf(payment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
            } returns PgPaymentListResponse(
                orderId = "200",
                transactions = emptyList(),
            )
            every { orderService.findById(200L) } returns order
            every {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "PG에 결제 기록 없음",
                    orderItems = any(),
                )
            } just Runs

            // when
            scheduler.checkInProgressPayments()

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
                status = PaymentStatus.IN_PROGRESS,
                createdAt = ZonedDateTime.now().minusMinutes(2),
            )
            val order = createMockOrder(orderId = 200L)

            every { paymentService.findInProgressPayments(any()) } returns listOf(payment)
            every {
                pgClient.getPaymentsByOrderId(100L, "200")
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
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) {
                paymentResultHandler.handlePaymentFailure(
                    paymentId = 1L,
                    reason = "PG에 결제 기록 없음",
                    orderItems = any(),
                )
            }
        }
    }

    private fun createMockPayment(
        id: Long,
        userId: Long,
        orderId: Long,
        status: PaymentStatus,
        createdAt: ZonedDateTime,
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
}
