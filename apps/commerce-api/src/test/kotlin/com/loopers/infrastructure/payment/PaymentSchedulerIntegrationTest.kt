package com.loopers.infrastructure.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * PaymentScheduler 통합 테스트
 *
 * - 실제 DB와 스프링 컨텍스트를 사용하여 배치 처리 결과를 검증
 * - PgClient만 모킹하여 PG 응답을 시뮬레이션
 */
@SpringBootTest
@DisplayName("PaymentScheduler 통합 테스트")
class PaymentSchedulerIntegrationTest @Autowired constructor(
    private val paymentScheduler: PaymentScheduler,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("checkInProgressPayments")
    inner class CheckInProgressPayments {

        @Test
        @DisplayName("여러 IN_PROGRESS 결제를 모두 처리한다")
        fun `processes multiple IN_PROGRESS payments`() {
            // given
            val paymentCount = 3
            val payments = (1..paymentCount).map { idx ->
                val payment = createInProgressPayment(externalPaymentKey = "tx_multi_$idx")
                every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(
                    createTransaction(
                        transactionKey = "tx_multi_$idx",
                        paymentId = payment.id,
                        status = PgTransactionStatus.SUCCESS,
                    ),
                )
                payment
            }

            // when
            paymentScheduler.checkInProgressPayments()

            // then
            payments.forEach { payment ->
                val updated = paymentRepository.findById(payment.id)!!
                assertThat(updated.status).isEqualTo(PaymentStatus.PAID)
            }
        }

        @Test
        @DisplayName("일부 결제에서 예외가 발생해도 나머지 결제는 정상 처리된다")
        fun `continues processing when some payments throw exceptions`() {
            // given
            val payment1 = createInProgressPayment(externalPaymentKey = "tx_error_1")
            val payment2 = createInProgressPayment(externalPaymentKey = "tx_error_2")
            val payment3 = createInProgressPayment(externalPaymentKey = "tx_error_3")

            // payment1 - 예외 발생
            every { pgClient.findTransactionsByPaymentId(payment1.id) } throws
                    RuntimeException("PG 연결 오류")

            // payment2, payment3 - 정상 처리
            every { pgClient.findTransactionsByPaymentId(payment2.id) } returns listOf(
                createTransaction(
                    transactionKey = "tx_error_2",
                    paymentId = payment2.id,
                    status = PgTransactionStatus.SUCCESS,
                ),
            )
            every { pgClient.findTransactionsByPaymentId(payment3.id) } returns listOf(
                createTransaction(
                    transactionKey = "tx_error_3",
                    paymentId = payment3.id,
                    status = PgTransactionStatus.SUCCESS,
                ),
            )

            // when
            paymentScheduler.checkInProgressPayments()

            // then
            val updatedPayment1 = paymentRepository.findById(payment1.id)!!
            val updatedPayment2 = paymentRepository.findById(payment2.id)!!
            val updatedPayment3 = paymentRepository.findById(payment3.id)!!

            assertAll(
                { assertThat(updatedPayment1.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
                { assertThat(updatedPayment2.status).isEqualTo(PaymentStatus.PAID) },
                { assertThat(updatedPayment3.status).isEqualTo(PaymentStatus.PAID) },
            )
        }
    }

    // ===========================================
    // 헬퍼 메서드
    // ===========================================

    private fun createOrder(
        userId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
    ): Order {
        val order = Order.place(userId)
        order.addOrderItem(
            productId = 1L,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = totalAmount,
        )
        return orderRepository.save(order)
    }

    private fun createInProgressPayment(
        externalPaymentKey: String = "tx_test",
        userId: Long = 1L,
    ): Payment {
        val order = createOrder()
        val payment = Payment.create(
            userId = userId,
            order = order,
            usedPoint = Money.krw(5000),
        )
        payment.initiate(PgPaymentCreateResult.Accepted(externalPaymentKey), Instant.now())
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
