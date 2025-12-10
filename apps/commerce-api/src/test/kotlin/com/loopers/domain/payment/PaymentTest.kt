package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.Test

class PaymentTest {

    @DisplayName("create 테스트")
    @Nested
    inner class Create {

        @DisplayName("주문 정보로 결제가 생성된다")
        @Test
        fun `create payment when provide order`() {
            // given
            val userId = 1L
            val orderId = 1L
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = userId,
                orderId = orderId,
                totalAmount = totalAmount,
                usedPoint = totalAmount,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.orderId).isEqualTo(orderId)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(totalAmount)
            assertThat(payment.usedPoint).isEqualTo(totalAmount)
        }

        @DisplayName("포인트로 전액 결제해도 PENDING 상태로 생성된다")
        @Test
        fun `have pending status when point covers total amount`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = totalAmount,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("포인트로 전액 결제하지 않으면 PENDING 상태로 생성된다")
        @Test
        fun `have pending status when point does not cover total amount`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.paidAmount).isEqualTo(totalAmount)
        }

        @DisplayName("사용 포인트가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when used point is negative`() {
            // given
            val totalAmount = Money.krw(10000)
            val negativePoint = Money.krw(-1000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.create(
                    userId = 1L,
                    orderId = 1L,
                    totalAmount = totalAmount,
                    usedPoint = negativePoint,
                    issuedCouponId = null,
                    couponDiscount = Money.ZERO_KRW,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트는 0 이상이어야 합니다")
        }

        @DisplayName("포인트+쿠폰 할인이 주문 금액을 초과하면 예외가 발생한다")
        @Test
        fun `throws exception when point plus coupon exceeds total amount`() {
            // given
            val totalAmount = Money.krw(10000)

            // when - 포인트 11000 > 주문금액 10000
            val exception = assertThrows<CoreException> {
                Payment.create(
                    userId = 1L,
                    orderId = 1L,
                    totalAmount = totalAmount,
                    usedPoint = Money.krw(11000),
                    issuedCouponId = null,
                    couponDiscount = Money.ZERO_KRW,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("포인트와 쿠폰 할인의 합이 주문 금액을 초과합니다")
        }

        @DisplayName("쿠폰 할인을 적용하여 결제가 생성된다")
        @Test
        fun `create payment with coupon discount`() {
            // given
            val totalAmount = Money.krw(10000)
            val couponDiscount = Money.krw(3000)
            val usedPoint = Money.krw(7000)
            val issuedCouponId = 100L

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
            )

            // then
            assertThat(payment.totalAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.couponDiscount).isEqualTo(couponDiscount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.issuedCouponId).isEqualTo(issuedCouponId)
            assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("쿠폰 없이 결제가 생성되면 쿠폰 할인이 0원이다")
        @Test
        fun `create payment without coupon has zero discount`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = totalAmount,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.couponDiscount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.issuedCouponId).isNull()
            assertThat(payment.usedPoint).isEqualTo(totalAmount)
        }

        @DisplayName("쿠폰 할인이 주문 금액과 같아도 PENDING 상태로 생성된다")
        @Test
        fun `have pending status when coupon discount equals order amount`() {
            // given
            val totalAmount = Money.krw(10000)

            // 쿠폰 할인이 주문 금액과 같음
            val couponDiscount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = 100L,
                couponDiscount = couponDiscount,
            )

            // then
            assertThat(payment.usedPoint).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.couponDiscount).isEqualTo(couponDiscount)
            assertThat(payment.totalAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("포인트와 카드 혼합 결제가 생성된다")
        @Test
        fun `create mixed payment with point and card`() {
            // given
            val totalAmount = Money.krw(10000)
            val usedPoint = Money.krw(3000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then - paidAmount = 10000 - 3000 = 7000 자동 계산
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.paidAmount).isEqualTo(Money.krw(7000))
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("카드 전액 결제가 생성된다")
        @Test
        fun `create card only payment`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then - paidAmount = 10000 자동 계산
            assertThat(payment.usedPoint).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.paidAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("쿠폰 + 포인트 + 카드 혼합 결제가 생성된다")
        @Test
        fun `create payment with coupon point and card`() {
            // given
            val totalAmount = Money.krw(10000)
            val couponDiscount = Money.krw(3000)
            val usedPoint = Money.krw(2000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                issuedCouponId = 100L,
                couponDiscount = couponDiscount,
            )

            // then - paidAmount = 10000 - 3000 - 2000 = 5000 자동 계산
            assertThat(payment.totalAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.couponDiscount).isEqualTo(couponDiscount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.paidAmount).isEqualTo(Money.krw(5000))
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        }
    }

    @DisplayName("of 팩토리 메서드 테스트")
    @Nested
    inner class Of {

        @DisplayName("직접 값을 지정하여 결제가 생성된다")
        @Test
        fun `create payment when provide specified values`() {
            // given
            val orderId = 1L
            val userId = 1L
            val totalAmount = Money.krw(50000)
            val usedPoint = Money.krw(5000)
            val status = PaymentStatus.PAID

            // when
            val payment = Payment.of(
                orderId = orderId,
                userId = userId,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                status = status,
            )

            // then
            assertThat(payment.orderId).isEqualTo(orderId)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(totalAmount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.status).isEqualTo(status)
        }
    }

    @DisplayName("신규 필드 테스트")
    @Nested
    inner class NewFields {

        @DisplayName("externalPaymentKey가 null로 초기화된다")
        @Test
        fun `externalPaymentKey is initialized as null`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = totalAmount,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.externalPaymentKey).isNull()
        }

        @DisplayName("failureMessage가 null로 초기화된다")
        @Test
        fun `failureMessage is initialized as null`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = totalAmount,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.failureMessage).isNull()
        }

        @DisplayName("version이 0으로 초기화된다")
        @Test
        fun `version is initialized as 0`() {
            // given
            val totalAmount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                orderId = 1L,
                totalAmount = totalAmount,
                usedPoint = totalAmount,
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.version).isEqualTo(0L)
        }
    }

    @DisplayName("initiate 테스트")
    @Nested
    inner class Initiate {

        @DisplayName("PENDING 상태에서 Accepted로 initiate() 호출 시 IN_PROGRESS로 전이되고 transactionKey가 저장된다")
        @Test
        fun `transitions to IN_PROGRESS with transactionKey when initiate with Accepted`() {
            // given
            val payment = createPendingPayment()
            val transactionKey = "tx_12345"
            val attemptedAt = Instant.now()

            // when
            payment.initiate(PgPaymentCreateResult.Accepted(transactionKey), attemptedAt)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(payment.externalPaymentKey).isEqualTo(transactionKey)
            assertThat(payment.attemptedAt).isEqualTo(attemptedAt)
        }

        @DisplayName("PENDING 상태에서 Uncertain으로 initiate() 호출 시 IN_PROGRESS로 전이되고 transactionKey는 null이다")
        @Test
        fun `transitions to IN_PROGRESS without transactionKey when initiate with Uncertain`() {
            // given
            val payment = createPendingPayment()
            val attemptedAt = Instant.now()

            // when
            payment.initiate(PgPaymentCreateResult.Uncertain, attemptedAt)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(payment.externalPaymentKey).isNull()
            assertThat(payment.attemptedAt).isEqualTo(attemptedAt)
        }

        @DisplayName("PENDING 상태에서 NotRequired로 initiate() 호출 시 PAID로 전이된다")
        @Test
        fun `transitions to PAID when initiate with NotRequired`() {
            // given
            val payment = createPendingPayment()
            val attemptedAt = Instant.now()

            // when
            payment.initiate(PgPaymentCreateResult.NotRequired, attemptedAt)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
            assertThat(payment.externalPaymentKey).isNull()
            assertThat(payment.attemptedAt).isEqualTo(attemptedAt)
        }

        @DisplayName("PENDING이 아닌 상태에서 initiate() 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when initiate is called from non-PENDING state`() {
            // given
            val payment = createPendingPayment()
            payment.initiate(PgPaymentCreateResult.Accepted("tx_first"), Instant.now())

            // when
            val exception = assertThrows<CoreException> {
                payment.initiate(PgPaymentCreateResult.Accepted("tx_second"), Instant.now())
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("결제 대기 상태에서만 결제를 개시할 수 있습니다")
        }
    }

    @DisplayName("confirmPayment 테스트")
    @Nested
    inner class ConfirmPayment {

        @DisplayName("externalPaymentKey로 SUCCESS 매칭 시 PAID로 전이된다")
        @Test
        fun `transitions to PAID when matched by externalPaymentKey with SUCCESS`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_12345")
            val transaction = createTransaction(
                transactionKey = "tx_12345",
                status = PgTransactionStatus.SUCCESS,
            )

            // when
            payment.confirmPayment(listOf(transaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
            assertThat(payment.externalPaymentKey).isEqualTo("tx_12345")
        }

        @DisplayName("externalPaymentKey로 FAILED 매칭 시 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when matched by externalPaymentKey with FAILED`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_12345")
            val transaction = createTransaction(
                transactionKey = "tx_12345",
                status = PgTransactionStatus.FAILED,
                failureReason = "잔액 부족",
            )

            // when
            payment.confirmPayment(listOf(transaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.externalPaymentKey).isEqualTo("tx_12345")
            assertThat(payment.failureMessage).isEqualTo("잔액 부족")
        }

        @DisplayName("paymentId로 SUCCESS 매칭 시 PAID로 전이하고 키가 설정된다")
        @Test
        fun `transitions to PAID and sets key when matched by paymentId with SUCCESS`() {
            // given - Uncertain으로 initiate된 경우 externalPaymentKey가 null
            val payment = createInProgressPayment(externalPaymentKey = null)
            val transaction = createTransaction(
                transactionKey = "tx_new_12345",
                paymentId = payment.id,
                status = PgTransactionStatus.SUCCESS,
            )

            // when
            payment.confirmPayment(listOf(transaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
            assertThat(payment.externalPaymentKey).isEqualTo("tx_new_12345")
        }

        @DisplayName("paymentId로 FAILED 매칭 시 FAILED로 전이하고 키가 설정된다")
        @Test
        fun `transitions to FAILED and sets key when matched by paymentId with FAILED`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = null)
            val transaction = createTransaction(
                transactionKey = "tx_failed_12345",
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "카드 한도 초과",
            )

            // when
            payment.confirmPayment(listOf(transaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.externalPaymentKey).isEqualTo("tx_failed_12345")
            assertThat(payment.failureMessage).isEqualTo("카드 한도 초과")
        }

        @DisplayName("IN_PROGRESS가 아닌 상태에서 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when called from non-IN_PROGRESS state`() {
            // given
            val payment = createPendingPayment() // PENDING 상태
            val transaction = createTransaction()

            // when
            val exception = assertThrows<CoreException> {
                payment.confirmPayment(listOf(transaction), currentTime = Instant.now())
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("결제 진행 중 상태에서만 확정할 수 있습니다")
        }

        @DisplayName("빈 트랜잭션 목록이면 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when transaction list is empty`() {
            // given
            val payment = createInProgressPayment()

            // when
            payment.confirmPayment(emptyList(), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("매칭되는 PG 트랜잭션이 없습니다")
        }

        @DisplayName("매칭되는 트랜잭션이 없으면 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when no matching transaction exists`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_12345")
            val unmatchedTransaction = createTransaction(
                transactionKey = "tx_different",
                status = PgTransactionStatus.SUCCESS,
            )

            // when
            payment.confirmPayment(listOf(unmatchedTransaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("매칭되는 PG 트랜잭션이 없습니다")
        }

        @DisplayName("PENDING 트랜잭션만 있으면 FAILED로 전이된다 (externalPaymentKey 없는 경우)")
        @Test
        fun `transitions to FAILED when only PENDING transactions exist without key`() {
            // given - externalPaymentKey가 null이면 PENDING 트랜잭션은 매칭 대상 아님
            val payment = createInProgressPayment(externalPaymentKey = null)
            val pendingTransaction = createTransaction(
                status = PgTransactionStatus.PENDING,
            )

            // when
            payment.confirmPayment(listOf(pendingTransaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("매칭되는 PG 트랜잭션이 없습니다")
        }

        @DisplayName("PENDING 트랜잭션 매칭 시 5분 경과하면 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when matched PENDING and 5 minutes elapsed`() {
            // given - externalPaymentKey가 있으면 PENDING도 매칭됨
            val attemptedAt = Instant.now().minusSeconds(301) // 5분 1초 전
            val payment = createInProgressPayment(
                externalPaymentKey = "tx_12345",
                attemptedAt = attemptedAt,
            )
            val pendingTransaction = createTransaction(
                transactionKey = "tx_12345",
                status = PgTransactionStatus.PENDING,
            )

            // when
            payment.confirmPayment(listOf(pendingTransaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("결제 시간 초과")
        }

        @DisplayName("PENDING 트랜잭션 매칭 시 5분 미경과면 IN_PROGRESS 유지된다")
        @Test
        fun `stays IN_PROGRESS when matched PENDING and less than 5 minutes elapsed`() {
            // given - externalPaymentKey가 있으면 PENDING도 매칭됨
            val attemptedAt = Instant.now().minusSeconds(299) // 4분 59초 전
            val payment = createInProgressPayment(
                externalPaymentKey = "tx_12345",
                attemptedAt = attemptedAt,
            )
            val pendingTransaction = createTransaction(
                transactionKey = "tx_12345",
                status = PgTransactionStatus.PENDING,
            )

            // when
            payment.confirmPayment(listOf(pendingTransaction), currentTime = Instant.now())

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @DisplayName("PAID 상태에서 confirmPayment 호출 시 PAID 상태 유지된다 (멱등성)")
        @Test
        fun `stays PAID when confirmPayment called on PAID payment`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_12345")
            val successTransaction = createTransaction(
                transactionKey = "tx_12345",
                status = PgTransactionStatus.SUCCESS,
            )
            payment.confirmPayment(listOf(successTransaction), currentTime = Instant.now())
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID) // 사전 조건 확인

            // when - PAID 상태에서 다시 호출
            payment.confirmPayment(listOf(successTransaction), currentTime = Instant.now())

            // then - 예외 없이 PAID 상태 유지
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("FAILED 상태에서 confirmPayment 호출 시 FAILED 상태 유지된다 (멱등성)")
        @Test
        fun `stays FAILED when confirmPayment called on FAILED payment`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_12345")
            val failedTransaction = createTransaction(
                transactionKey = "tx_12345",
                status = PgTransactionStatus.FAILED,
                failureReason = "잔액 부족",
            )
            payment.confirmPayment(listOf(failedTransaction), currentTime = Instant.now())
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) // 사전 조건 확인

            // when - FAILED 상태에서 다시 호출
            payment.confirmPayment(listOf(failedTransaction), currentTime = Instant.now())

            // then - 예외 없이 FAILED 상태 유지
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("잔액 부족")
        }
    }

    private fun createPendingPayment(
        userId: Long = 1L,
        orderId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
    ): Payment {
        return Payment.create(
            userId = userId,
            orderId = orderId,
            totalAmount = totalAmount,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
        )
    }

    private fun createInProgressPayment(
        userId: Long = 1L,
        orderId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
        externalPaymentKey: String? = "tx_12345",
        attemptedAt: Instant = Instant.now(),
    ): Payment {
        val payment = Payment.create(
            userId = userId,
            orderId = orderId,
            totalAmount = totalAmount,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
        )
        val result = if (externalPaymentKey != null) {
            PgPaymentCreateResult.Accepted(externalPaymentKey)
        } else {
            PgPaymentCreateResult.Uncertain
        }
        payment.initiate(result, attemptedAt)
        return payment
    }

    private fun createTransaction(
        transactionKey: String = "tx_default",
        paymentId: Long = 1L,
        status: PgTransactionStatus = PgTransactionStatus.SUCCESS,
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
