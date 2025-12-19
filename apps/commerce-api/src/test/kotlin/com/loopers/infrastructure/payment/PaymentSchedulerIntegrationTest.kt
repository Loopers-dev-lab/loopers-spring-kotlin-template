package com.loopers.infrastructure.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
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
import org.springframework.test.context.TestPropertySource
import java.time.Instant

/**
 * PaymentScheduler 통합 테스트
 *
 * - 실제 DB와 스프링 컨텍스트를 사용하여 배치 처리 결과를 검증
 * - PgClient만 모킹하여 PG 응답을 시뮬레이션
 */
@SpringBootTest
@DisplayName("PaymentScheduler 통합 테스트")
@TestPropertySource(properties = ["scheduler.enabled=true"])
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
        clearMocks(pgClient)
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
                every { pgClient.findTransaction("tx_multi_$idx") } returns createTransaction(
                    transactionKey = "tx_multi_$idx",
                    paymentId = payment.id,
                    status = PgTransactionStatus.SUCCESS,
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
            every { pgClient.findTransaction("tx_error_1") } throws
                    RuntimeException("PG 연결 오류")

            // payment2, payment3 - 정상 처리
            every { pgClient.findTransaction("tx_error_2") } returns createTransaction(
                transactionKey = "tx_error_2",
                paymentId = payment2.id,
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction("tx_error_3") } returns createTransaction(
                transactionKey = "tx_error_3",
                paymentId = payment3.id,
                status = PgTransactionStatus.SUCCESS,
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

    @Nested
    @DisplayName("recoverPendingPayments")
    inner class RecoverPendingPayments {

        @Test
        @DisplayName("스케줄러가 정상적으로 실행된다")
        fun `scheduler executes without error`() {
            // given - PENDING 결제가 없어도 스케줄러는 정상 실행되어야 함

            // when - 스케줄러 호출
            paymentScheduler.recoverPendingPayments()

            // then - 예외 없이 정상 실행됨 (검증은 PaymentJobIntegrationTest에서 수행)
        }

        @Test
        @DisplayName("최근 생성된 PENDING 결제는 처리하지 않는다")
        fun `skips recently created PENDING payments`() {
            // given - 결제 생성 직후에 recoverPendingPayments 호출
            // threshold는 now() - 30초이므로 방금 생성된 결제는 대상이 아님

            val payment = createPendingPayment()

            // 스케줄러 호출 - threshold가 현재 시간 - 30초이므로 방금 생성된 결제는 대상 아님
            paymentScheduler.recoverPendingPayments()

            // then - 결제 상태는 여전히 PENDING
            val updated = paymentRepository.findById(payment.id)!!
            assertThat(updated.status).isEqualTo(PaymentStatus.PENDING)
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
            orderId = order.id,
            totalAmount = order.totalAmount,
            usedPoint = Money.krw(5000),
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
            cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
        )
        payment.initiate(PgPaymentCreateResult.Accepted(externalPaymentKey), Instant.now())
        return paymentRepository.save(payment)
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
