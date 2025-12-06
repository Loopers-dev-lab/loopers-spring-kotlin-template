package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.values.Money
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.ZonedDateTime

@DisplayName("PaymentScheduler 단위 테스트")
class PaymentSchedulerTest {
    private lateinit var paymentFacade: PaymentFacade
    private lateinit var scheduler: PaymentScheduler

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        paymentFacade = mockk(relaxed = true)
        scheduler = PaymentScheduler(paymentFacade, SimpleMeterRegistry())
    }

    @Nested
    @DisplayName("IN_PROGRESS 결제 처리")
    inner class ProcessInProgressPayments {

        @Test
        @DisplayName("IN_PROGRESS 결제가 있으면 Facade에 위임하여 처리한다")
        fun `delegates to Facade when IN_PROGRESS payments exist`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(id = paymentId)
            every { paymentFacade.findInProgressPayments() } returns listOf(payment)

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId) }
        }

        @Test
        @DisplayName("IN_PROGRESS 결제가 없으면 아무 작업도 하지 않는다")
        fun `does nothing when no IN_PROGRESS payments`() {
            // given
            every { paymentFacade.findInProgressPayments() } returns emptyList()

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 0) { paymentFacade.processInProgressPayment(any<Long>()) }
        }

        @Test
        @DisplayName("여러 결제를 순차적으로 처리한다")
        fun `processes multiple payments sequentially`() {
            // given
            val paymentId1 = 1L
            val paymentId2 = 2L
            val paymentId3 = 3L
            val payment1 = createMockPayment(id = paymentId1)
            val payment2 = createMockPayment(id = paymentId2)
            val payment3 = createMockPayment(id = paymentId3)

            every { paymentFacade.findInProgressPayments() } returns listOf(payment1, payment2, payment3)

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId2) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId3) }
        }
    }

    @Nested
    @DisplayName("낙관적 락 충돌 처리")
    inner class OptimisticLockConflict {

        @Test
        @DisplayName("낙관적 락 충돌 시 해당 결제를 건너뛰고 다음 결제를 처리한다")
        fun `skips payment on optimistic lock conflict and continues with next`() {
            // given
            val paymentId1 = 1L
            val paymentId2 = 2L
            val payment1 = createMockPayment(id = paymentId1)
            val payment2 = createMockPayment(id = paymentId2)

            every { paymentFacade.findInProgressPayments() } returns listOf(payment1, payment2)

            // payment1 - 낙관적 락 충돌
            every { paymentFacade.processInProgressPayment(paymentId1) } throws
                ObjectOptimisticLockingFailureException(Payment::class.java, paymentId1)

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId2) }
        }
    }

    @Nested
    @DisplayName("예외 처리")
    inner class ExceptionHandling {

        @Test
        @DisplayName("예상치 못한 예외 발생 시 해당 결제를 건너뛰고 다음 결제를 처리한다")
        fun `skips payment on unexpected exception and continues with next`() {
            // given
            val paymentId1 = 1L
            val paymentId2 = 2L
            val payment1 = createMockPayment(id = paymentId1)
            val payment2 = createMockPayment(id = paymentId2)

            every { paymentFacade.findInProgressPayments() } returns listOf(payment1, payment2)

            // payment1 - 예상치 못한 예외
            every { paymentFacade.processInProgressPayment(paymentId1) } throws RuntimeException("Unexpected error")

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId2) }
        }
    }

    @Nested
    @DisplayName("PG 연결 실패 처리")
    inner class PgConnectionFailure {

        @Test
        @DisplayName("PG 연결 실패 시 해당 결제를 건너뛰고 다음 결제를 처리한다")
        fun `skips payment on PG connection failure and continues with next`() {
            // given
            val paymentId1 = 1L
            val paymentId2 = 2L
            val payment1 = createMockPayment(id = paymentId1)
            val payment2 = createMockPayment(id = paymentId2)

            every { paymentFacade.findInProgressPayments() } returns listOf(payment1, payment2)

            // payment1 - PG 연결 실패
            every { paymentFacade.processInProgressPayment(paymentId1) } throws
                PgRequestNotReachedException("PG 연결 실패")

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(paymentId2) }
        }
    }

    private fun createMockPayment(
        id: Long = 1L,
        userId: Long = 100L,
        orderId: Long = 200L,
        status: PaymentStatus = PaymentStatus.IN_PROGRESS,
        createdAt: ZonedDateTime = ZonedDateTime.now().minusMinutes(2),
    ): Payment {
        val payment = mockk<Payment>(relaxed = true)
        every { payment.id } returns id
        every { payment.userId } returns userId
        every { payment.orderId } returns orderId
        every { payment.status } returns status
        every { payment.createdAt } returns createdAt
        every { payment.usedPoint } returns Money.krw(0)
        every { payment.issuedCouponId } returns null
        return payment
    }
}
