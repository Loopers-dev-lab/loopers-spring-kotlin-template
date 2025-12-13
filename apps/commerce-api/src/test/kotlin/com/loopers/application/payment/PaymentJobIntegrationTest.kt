package com.loopers.application.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

/**
 * PaymentJob 통합 테스트
 *
 * - 실제 DB와 스프링 컨텍스트를 사용하여 배치 처리 결과를 검증
 * - PgClient만 모킹하여 PG 응답을 시뮬레이션
 */
@SpringBootTest
@DisplayName("PaymentJob 통합 테스트")
class PaymentJobIntegrationTest @Autowired constructor(
    private val paymentJob: PaymentJob,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        clearMocks(pgClient)
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("recoverPendingPayments")
    inner class RecoverPendingPayments {

        @Test
        @DisplayName("threshold 이전에 생성된 PENDING 결제를 처리한다")
        fun `processes PENDING payments created before threshold`() {
            // given
            val payment1 = createPendingPayment()
            val payment2 = createPendingPayment()

            every { pgClient.requestPayment(match { it.paymentId == payment1.id }) } returns
                PgPaymentCreateResult.Accepted("tx_test_1")
            every { pgClient.requestPayment(match { it.paymentId == payment2.id }) } returns
                PgPaymentCreateResult.Accepted("tx_test_2")

            // threshold: 미래 시각 (모든 결제 포함)
            val threshold = ZonedDateTime.now().plusMinutes(1)

            // when
            val result = paymentJob.recoverPendingPayments(threshold)

            // then
            assertAll(
                { assertThat(result.processed).isEqualTo(2) },
                { assertThat(result.skipped).isEqualTo(0) },
            )

            val updated1 = paymentRepository.findById(payment1.id)!!
            val updated2 = paymentRepository.findById(payment2.id)!!
            assertAll(
                { assertThat(updated1.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
                { assertThat(updated2.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
            )
        }

        @Test
        @DisplayName("threshold 이후에 생성된 PENDING 결제는 처리하지 않는다")
        fun `skips PENDING payments created after threshold`() {
            // given
            createPendingPayment()
            createPendingPayment()

            // threshold: 과거 시각 (모든 결제 제외)
            val threshold = ZonedDateTime.now().minusMinutes(10)

            // when
            val result = paymentJob.recoverPendingPayments(threshold)

            // then
            assertAll(
                { assertThat(result.processed).isEqualTo(0) },
                { assertThat(result.skipped).isEqualTo(0) },
            )
        }

        @Test
        @DisplayName("일부 결제 처리에서 예외가 발생해도 나머지 결제를 계속 처리한다")
        fun `continues processing when one payment fails`() {
            // given
            val payment1 = createPendingPayment()
            val payment2 = createPendingPayment()
            val payment3 = createPendingPayment()

            // payment1 - 예외 발생
            every { pgClient.requestPayment(match { it.paymentId == payment1.id }) } throws
                RuntimeException("PG 연결 오류")

            // payment2, payment3 - 정상 처리
            every { pgClient.requestPayment(match { it.paymentId == payment2.id }) } returns
                PgPaymentCreateResult.Accepted("tx_2")
            every { pgClient.requestPayment(match { it.paymentId == payment3.id }) } returns
                PgPaymentCreateResult.Accepted("tx_3")

            val threshold = ZonedDateTime.now().plusMinutes(1)

            // when
            val result = paymentJob.recoverPendingPayments(threshold)

            // then
            assertAll(
                { assertThat(result.processed).isEqualTo(2) },
                { assertThat(result.skipped).isEqualTo(1) },
            )

            val updated1 = paymentRepository.findById(payment1.id)!!
            val updated2 = paymentRepository.findById(payment2.id)!!
            val updated3 = paymentRepository.findById(payment3.id)!!
            assertAll(
                { assertThat(updated1.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(updated2.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
                { assertThat(updated3.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
            )
        }

        @Test
        @DisplayName("올바른 JobResult 카운트를 반환한다")
        fun `returns correct JobResult counts`() {
            // given
            val payment1 = createPendingPayment()
            val payment2 = createPendingPayment()
            val payment3 = createPendingPayment()

            every { pgClient.requestPayment(match { it.paymentId == payment1.id }) } returns
                PgPaymentCreateResult.Accepted("tx_1")
            every { pgClient.requestPayment(match { it.paymentId == payment2.id }) } throws
                RuntimeException("Error")
            every { pgClient.requestPayment(match { it.paymentId == payment3.id }) } returns
                PgPaymentCreateResult.Accepted("tx_3")

            val threshold = ZonedDateTime.now().plusMinutes(1)

            // when
            val result = paymentJob.recoverPendingPayments(threshold)

            // then
            assertThat(result).isEqualTo(JobResult(processed = 2, skipped = 1))
        }
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private fun createOrder(
        userId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
    ): Order {
        val order = Order.place(userId)
        order.addOrderItem(
            productId = 1L,
            quantity = 1,
            productName = "Test Product",
            unitPrice = totalAmount,
        )
        return orderRepository.save(order)
    }

    private fun createPendingPayment(
        userId: Long = 1L,
    ): Payment {
        val order = createOrder()
        val payment = Payment.create(
            userId = userId,
            orderId = order.id,
            totalAmount = order.totalAmount,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
            cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
        )
        return paymentRepository.save(payment)
    }
}
