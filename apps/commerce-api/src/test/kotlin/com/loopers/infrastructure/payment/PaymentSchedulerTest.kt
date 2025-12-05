package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentStatus
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
import java.time.ZonedDateTime

@DisplayName("PaymentScheduler 단위 테스트")
class PaymentSchedulerTest {
    private lateinit var paymentFacade: PaymentFacade
    private lateinit var scheduler: PaymentScheduler

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        paymentFacade = mockk()
        scheduler = PaymentScheduler(paymentFacade)
    }

    @Nested
    @DisplayName("IN_PROGRESS 결제 처리")
    inner class ProcessInProgressPayments {

        @Test
        @DisplayName("IN_PROGRESS 결제가 있으면 Facade에 위임하여 처리한다")
        fun `delegates to Facade when IN_PROGRESS payments exist`() {
            // given
            val payment = createMockPayment(id = 1L)
            every { paymentFacade.findInProgressPayments(any()) } returns listOf(payment)
            every { paymentFacade.processInProgressPayment(payment) } just Runs

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment) }
        }

        @Test
        @DisplayName("IN_PROGRESS 결제가 없으면 아무 작업도 하지 않는다")
        fun `does nothing when no IN_PROGRESS payments`() {
            // given
            every { paymentFacade.findInProgressPayments(any()) } returns emptyList()

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 0) { paymentFacade.processInProgressPayment(any()) }
        }

        @Test
        @DisplayName("여러 결제를 순차적으로 처리한다")
        fun `processes multiple payments sequentially`() {
            // given
            val payment1 = createMockPayment(id = 1L)
            val payment2 = createMockPayment(id = 2L)
            val payment3 = createMockPayment(id = 3L)

            every { paymentFacade.findInProgressPayments(any()) } returns listOf(payment1, payment2, payment3)
            every { paymentFacade.processInProgressPayment(any()) } just Runs

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment2) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment3) }
        }
    }

    @Nested
    @DisplayName("낙관적 락 충돌 처리")
    inner class OptimisticLockConflict {

        @Test
        @DisplayName("낙관적 락 충돌 시 해당 결제를 건너뛰고 다음 결제를 처리한다")
        fun `skips payment on optimistic lock conflict and continues with next`() {
            // given
            val payment1 = createMockPayment(id = 1L)
            val payment2 = createMockPayment(id = 2L)

            every { paymentFacade.findInProgressPayments(any()) } returns listOf(payment1, payment2)

            // payment1 - 낙관적 락 충돌
            every { paymentFacade.processInProgressPayment(payment1) } throws
                ObjectOptimisticLockingFailureException(Payment::class.java, payment1.id)

            // payment2 - 정상 처리
            every { paymentFacade.processInProgressPayment(payment2) } just Runs

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment2) }
        }
    }

    @Nested
    @DisplayName("예외 처리")
    inner class ExceptionHandling {

        @Test
        @DisplayName("예상치 못한 예외 발생 시 해당 결제를 건너뛰고 다음 결제를 처리한다")
        fun `skips payment on unexpected exception and continues with next`() {
            // given
            val payment1 = createMockPayment(id = 1L)
            val payment2 = createMockPayment(id = 2L)

            every { paymentFacade.findInProgressPayments(any()) } returns listOf(payment1, payment2)

            // payment1 - 예상치 못한 예외
            every { paymentFacade.processInProgressPayment(payment1) } throws RuntimeException("Unexpected error")

            // payment2 - 정상 처리
            every { paymentFacade.processInProgressPayment(payment2) } just Runs

            // when
            scheduler.checkInProgressPayments()

            // then
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment1) }
            verify(exactly = 1) { paymentFacade.processInProgressPayment(payment2) }
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
}
