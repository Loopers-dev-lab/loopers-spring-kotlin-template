package com.loopers.domain.order

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class PaymentServiceIntegrationTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("PENDING 결제 생성 통합테스트")
    @Nested
    inner class CreatePending {

        @DisplayName("PENDING 상태의 결제를 생성할 수 있다")
        @Test
        fun `create pending payment successfully`() {
            // given
            val order = createOrder() // totalAmount = 10000
            val usedPoint = Money.krw(5000)

            // when
            val payment = paymentService.createPending(
                userId = order.userId,
                order = order,
                usedPoint = usedPoint,
            )

            // then - paidAmount = 10000 - 5000 = 5000 자동 계산
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.usedPoint).isEqualTo(usedPoint) },
                { assertThat(payment.paidAmount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.orderId).isEqualTo(order.id) },
            )
        }

        @DisplayName("쿠폰 할인을 포함한 PENDING 결제를 생성할 수 있다")
        @Test
        fun `create pending payment with coupon discount`() {
            // given
            val order = createOrder(totalAmount = Money.krw(15000))
            val usedPoint = Money.krw(5000)
            val couponDiscount = Money.krw(3000)

            // when
            val payment = paymentService.createPending(
                userId = order.userId,
                order = order,
                usedPoint = usedPoint,
                issuedCouponId = 1L,
                couponDiscount = couponDiscount,
            )

            // then - paidAmount = 15000 - 5000 - 3000 = 7000 자동 계산
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.couponDiscount).isEqualTo(couponDiscount) },
                { assertThat(payment.issuedCouponId).isEqualTo(1L) },
                { assertThat(payment.paidAmount).isEqualTo(Money.krw(7000)) },
            )
        }
    }

    @DisplayName("결제 시작 (PENDING -> IN_PROGRESS) 통합테스트")
    @Nested
    inner class StartPayment {

        @DisplayName("PENDING 상태에서 IN_PROGRESS로 전이할 수 있다")
        @Test
        fun `start payment transitions to IN_PROGRESS`() {
            // given
            val payment = createPendingPayment()

            // when
            val updatedPayment = paymentService.startPayment(payment.id)

            // then
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @DisplayName("존재하지 않는 결제 ID로 시작하면 예외가 발생한다")
        @Test
        fun `throw exception when payment not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.startPayment(999L)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("결제 완료 (IN_PROGRESS -> PAID) 통합테스트")
    @Nested
    inner class CompletePayment {

        @DisplayName("IN_PROGRESS 상태에서 PAID로 전이할 수 있다")
        @Test
        fun `complete payment transitions to PAID`() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = "pg_tx_12345"

            // when
            val updatedPayment = paymentService.completePayment(payment.id, externalPaymentKey)

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID) },
                { assertThat(updatedPayment.externalPaymentKey).isEqualTo(externalPaymentKey) },
            )
        }

        @DisplayName("존재하지 않는 결제 ID로 완료하면 예외가 발생한다")
        @Test
        fun `throw exception when payment not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.completePayment(999L)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("결제 실패 (-> FAILED) 통합테스트")
    @Nested
    inner class FailPayment {

        @DisplayName("PENDING 상태에서 FAILED로 전이할 수 있다")
        @Test
        fun `fail payment from PENDING`() {
            // given
            val payment = createPendingPayment()
            val failureMessage = "결제 실패: 서킷브레이커 OPEN"

            // when
            val updatedPayment = paymentService.failPayment(payment.id, failureMessage)

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(updatedPayment.failureMessage).isEqualTo(failureMessage) },
            )
        }

        @DisplayName("IN_PROGRESS 상태에서 FAILED로 전이할 수 있다")
        @Test
        fun `fail payment from IN_PROGRESS`() {
            // given
            val payment = createInProgressPayment()
            val failureMessage = "PG 결제 실패"

            // when
            val updatedPayment = paymentService.failPayment(payment.id, failureMessage)

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(updatedPayment.failureMessage).isEqualTo(failureMessage) },
            )
        }
    }

    @DisplayName("IN_PROGRESS 결제 조회 통합테스트")
    @Nested
    inner class FindInProgressPayments {

        @DisplayName("임계값보다 오래된 IN_PROGRESS 결제를 조회할 수 있다")
        @Test
        fun `find in progress payments older than threshold`() {
            // given
            val inProgressPayment = createInProgressPayment()
            val threshold = ZonedDateTime.now().plusMinutes(1) // future threshold

            // when
            val payments = paymentService.findInProgressPayments(threshold)

            // then
            assertThat(payments).hasSize(1)
            assertThat(payments[0].id).isEqualTo(inProgressPayment.id)
        }
    }

    @DisplayName("외부 결제 키로 결제 조회 통합테스트")
    @Nested
    inner class FindByExternalPaymentKey {

        @DisplayName("외부 결제 키로 결제를 조회할 수 있다")
        @Test
        fun `find payment by external key`() {
            // given
            val payment = createInProgressPayment()
            payment.updateExternalPaymentKey("pg_tx_12345")
            paymentRepository.save(payment)

            // when
            val foundPayment = paymentService.findByExternalPaymentKey("pg_tx_12345")

            // then
            assertThat(foundPayment).isNotNull
            assertThat(foundPayment?.id).isEqualTo(payment.id)
        }

        @DisplayName("존재하지 않는 외부 결제 키로 조회하면 null을 반환한다")
        @Test
        fun `return null when external key not found`() {
            // when
            val foundPayment = paymentService.findByExternalPaymentKey("non_existent_key")

            // then
            assertThat(foundPayment).isNull()
        }
    }

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

    private fun createPendingPayment(
        userId: Long = 1L,
        usedPoint: Money = Money.krw(5000),
    ): Payment {
        val order = createOrder()
        val payment = Payment.create(
            userId = userId,
            order = order,
            usedPoint = usedPoint,
        )
        return paymentRepository.save(payment)
    }

    private fun createInProgressPayment(): Payment {
        val payment = createPendingPayment()
        payment.start()
        return paymentRepository.save(payment)
    }
}
