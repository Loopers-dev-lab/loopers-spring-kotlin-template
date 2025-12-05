package com.loopers.domain.order

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
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
            val order = createOrder(userId = userId)

            // when
            val payment = Payment.create(
                userId = userId,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.orderId).isEqualTo(order.id)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(order.totalAmount)
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
        }

        @DisplayName("포인트로 전액 결제하면 PAID 상태로 생성된다")
        @Test
        fun `have paid status when point covers total amount`() {
            // given
            val order = createOrder()

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("포인트로 전액 결제하지 않으면 PENDING 상태로 생성된다")
        @Test
        fun `have pending status when point does not cover total amount`() {
            // given
            val order = createOrder()

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.paidAmount).isEqualTo(order.totalAmount)
        }

        @DisplayName("사용 포인트가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when used point is negative`() {
            // given
            val order = createOrder()
            val negativePoint = Money.krw(-1000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.create(
                    userId = 1L,
                    order = order,
                    usedPoint = negativePoint,
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
            val order = createOrder() // totalAmount = 10000

            // when - 포인트 11000 > 주문금액 10000
            val exception = assertThrows<CoreException> {
                Payment.create(
                    userId = 1L,
                    order = order,
                    usedPoint = Money.krw(11000),
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
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val couponDiscount = Money.krw(3000)
            val usedPoint = Money.krw(7000)
            val issuedCouponId = 100L

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = usedPoint,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
            )

            // then
            assertThat(payment.totalAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.couponDiscount).isEqualTo(couponDiscount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.issuedCouponId).isEqualTo(issuedCouponId)
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("쿠폰 없이 결제가 생성되면 쿠폰 할인이 0원이다")
        @Test
        fun `create payment without coupon has zero discount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.couponDiscount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.issuedCouponId).isNull()
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
        }

        @DisplayName("쿠폰 할인이 주문 금액과 같으면 포인트는 0원이어야 한다")
        @Test
        fun `used point should be zero when coupon discount equals order amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )

            // 쿠폰 할인이 주문 금액과 같음
            val couponDiscount = Money.krw(10000)

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = 100L,
                couponDiscount = couponDiscount,
            )

            // then
            assertThat(payment.usedPoint).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.couponDiscount).isEqualTo(couponDiscount)
            assertThat(payment.totalAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("포인트와 카드 혼합 결제가 생성된다")
        @Test
        fun `create mixed payment with point and card`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val usedPoint = Money.krw(3000)

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = usedPoint,
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
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = Money.ZERO_KRW,
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
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val couponDiscount = Money.krw(3000)
            val usedPoint = Money.krw(2000)

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
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
            val order = createOrder()

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.externalPaymentKey).isNull()
        }

        @DisplayName("failureMessage가 null로 초기화된다")
        @Test
        fun `failureMessage is initialized as null`() {
            // given
            val order = createOrder()

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.failureMessage).isNull()
        }

        @DisplayName("version이 0으로 초기화된다")
        @Test
        fun `version is initialized as 0`() {
            // given
            val order = createOrder()

            // when
            val payment = Payment.create(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.version).isEqualTo(0L)
        }
    }

    @DisplayName("start 테스트")
    @Nested
    inner class Start {

        @DisplayName("PENDING 상태에서 start() 호출 시 IN_PROGRESS로 전이된다")
        @Test
        fun `transitions from PENDING to IN_PROGRESS when start is called`() {
            // given
            val payment = createPendingPayment()

            // when
            payment.start()

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @DisplayName("PENDING이 아닌 상태에서 start() 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when start is called from non-PENDING state`() {
            // given
            val payment = createPendingPayment()
            payment.start() // IN_PROGRESS 상태로 전이

            // when
            val exception = assertThrows<CoreException> {
                payment.start()
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("결제 대기 상태에서만 결제를 시작할 수 있습니다")
        }
    }

    @DisplayName("updateExternalPaymentKey 테스트")
    @Nested
    inner class UpdateExternalPaymentKey {

        @DisplayName("IN_PROGRESS 상태에서 externalPaymentKey를 저장할 수 있다")
        @Test
        fun `saves externalPaymentKey when in IN_PROGRESS state`() {
            // given
            val payment = createPendingPayment()
            payment.start()
            val paymentKey = "pg_txn_12345"

            // when
            payment.updateExternalPaymentKey(paymentKey)

            // then
            assertThat(payment.externalPaymentKey).isEqualTo(paymentKey)
        }

        @DisplayName("IN_PROGRESS가 아닌 상태에서 updateExternalPaymentKey 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when called from non-IN_PROGRESS state`() {
            // given
            val payment = createPendingPayment() // PENDING 상태

            // when
            val exception = assertThrows<CoreException> {
                payment.updateExternalPaymentKey("pg_txn_12345")
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("결제 진행 중 상태에서만 외부 결제 키를 저장할 수 있습니다")
        }
    }

    @DisplayName("success 테스트")
    @Nested
    inner class Success {

        @DisplayName("IN_PROGRESS 상태에서 success() 호출 시 PAID로 전이된다")
        @Test
        fun `transitions from IN_PROGRESS to PAID when success is called`() {
            // given
            val payment = createPendingPayment()
            payment.start()

            // when
            payment.paid()

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("IN_PROGRESS가 아닌 상태에서 success() 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when success is called from non-IN_PROGRESS state`() {
            // given
            val payment = createPendingPayment() // PENDING 상태

            // when
            val exception = assertThrows<CoreException> {
                payment.paid()
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("결제 진행 중 상태에서만 성공 처리할 수 있습니다")
        }
    }

    @DisplayName("fail 테스트")
    @Nested
    inner class Fail {

        @DisplayName("PENDING 상태에서 fail() 호출 시 FAILED로 전이된다")
        @Test
        fun `transitions from PENDING to FAILED when fail is called`() {
            // given
            val payment = createPendingPayment()

            // when
            payment.fail("서킷 오픈")

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("서킷 오픈")
        }

        @DisplayName("IN_PROGRESS 상태에서 fail() 호출 시 FAILED로 전이된다")
        @Test
        fun `transitions from IN_PROGRESS to FAILED when fail is called`() {
            // given
            val payment = createPendingPayment()
            payment.start()

            // when
            payment.fail("PG 거절")

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isEqualTo("PG 거절")
        }

        @DisplayName("failureMessage 없이 fail() 호출할 수 있다")
        @Test
        fun `can call fail without failureMessage`() {
            // given
            val payment = createPendingPayment()

            // when
            payment.fail(null)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureMessage).isNull()
        }

        @DisplayName("PAID 상태에서 fail() 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when fail is called from PAID state`() {
            // given
            val payment = createPendingPayment()
            payment.start()
            payment.paid()

            // when
            val exception = assertThrows<CoreException> {
                payment.fail("실패 시도")
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("이미 처리된 결제입니다")
        }

        @DisplayName("FAILED 상태에서 fail() 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when fail is called from FAILED state`() {
            // given
            val payment = createPendingPayment()
            payment.fail("첫 번째 실패")

            // when
            val exception = assertThrows<CoreException> {
                payment.fail("두 번째 실패 시도")
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("이미 처리된 결제입니다")
        }
    }

    private fun createPendingPayment(
        userId: Long = 1L,
        order: Order = createOrder(),
    ): Payment {
        return Payment.create(
            userId = userId,
            order = order,
            usedPoint = Money.ZERO_KRW,
        )
    }

    private fun createOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        productName: String = "테스트 상품",
        unitPrice: Money = Money.krw(10000),
    ): OrderItem {
        return OrderItem.create(
            productId = productId,
            quantity = quantity,
            productName = productName,
            unitPrice = unitPrice,
        )
    }

    private fun createOrder(
        userId: Long = 1L,
        orderItems: MutableList<OrderItem> = mutableListOf(createOrderItem()),
    ): Order {
        return Order.of(userId, Money.krw(10000), OrderStatus.PAID, orderItems)
    }
}
