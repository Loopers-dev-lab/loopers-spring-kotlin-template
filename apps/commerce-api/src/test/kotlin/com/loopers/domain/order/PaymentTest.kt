package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class PaymentTest {

    @DisplayName("pay 테스트")
    @Nested
    inner class Pay {

        @DisplayName("주문 정보로 결제가 생성된다")
        @Test
        fun `create payment when provide order`() {
            // given
            val userId = 1L
            val order = createOrder(userId = userId)

            // when
            val payment = Payment.pay(
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

        @DisplayName("결제 상태가 PAID로 설정된다")
        @Test
        fun `have paid status when paid`() {
            // given
            val order = createOrder()

            // when
            val payment = createPayment(order = order)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("사용 포인트가 주문 금액과 정확히 일치하면 결제가 생성된다")
        @Test
        fun `create payed payment when used point exactly matches total amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )

            // when
            val payment = createPayment(order = order)

            // then
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
            assertThat(payment.totalAmount).isEqualTo(order.totalAmount)
        }

        @DisplayName("사용 포인트가 주문 금액보다 부족하면 예외가 발생한다")
        @Test
        fun `throws exception when used point is less than total amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val insufficientPoint = Money.krw(5000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.pay(
                    userId = 1L,
                    order = order,
                    usedPoint = insufficientPoint,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트가 결제 금액과 일치하지 않습니다.")
        }

        @DisplayName("사용 포인트가 주문 금액을 초과하면 예외가 발생한다")
        @Test
        fun `throws exception when used point exceeds total amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val excessPoint = Money.krw(15000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.pay(
                    userId = 1L,
                    order = order,
                    usedPoint = excessPoint,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트가 결제 금액과 일치하지 않습니다.")
        }

        @DisplayName("사용 포인트가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when used point is negative`() {
            // given
            val order = createOrder()
            val negativePoint = Money.krw(-1000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.pay(
                    userId = 1L,
                    order = order,
                    usedPoint = negativePoint,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트는 0 이상이어야 합니다.")
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
            val payment = Payment.pay(
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
            val payment = Payment.pay(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.couponDiscount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.issuedCouponId).isNull()
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
        }

        @DisplayName("쿠폰 할인 적용 시 사용 포인트가 (주문 금액 - 쿠폰 할인)과 일치하지 않으면 예외가 발생한다")
        @Test
        fun `throws exception when used point does not match total amount minus coupon discount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val couponDiscount = Money.krw(3000)

            // 잘못된 포인트: 10000 - 3000 = 7000이어야 하는데 5000
            val incorrectPoint = Money.krw(5000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.pay(
                    userId = 1L,
                    order = order,
                    usedPoint = incorrectPoint,
                    issuedCouponId = 100L,
                    couponDiscount = couponDiscount,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트가 결제 금액과 일치하지 않습니다.")
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
            val payment = Payment.pay(
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

    @DisplayName("pending 팩토리 메서드 테스트")
    @Nested
    inner class Pending {

        @DisplayName("주문 정보로 결제가 PENDING 상태로 생성된다")
        @Test
        fun `create payment in PENDING status when provide order`() {
            // given
            val userId = 1L
            val order = createOrder(userId = userId)

            // when
            val payment = Payment.pending(
                userId = userId,
                order = order,
                usedPoint = order.totalAmount,
                paidAmount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.orderId).isEqualTo(order.id)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(order.totalAmount)
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
            assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
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
            val paidAmount = Money.krw(7000)

            // when
            val payment = Payment.pending(
                userId = 1L,
                order = order,
                usedPoint = usedPoint,
                paidAmount = paidAmount,
            )

            // then
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.paidAmount).isEqualTo(paidAmount)
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
            val payment = Payment.pending(
                userId = 1L,
                order = order,
                usedPoint = Money.ZERO_KRW,
                paidAmount = Money.krw(10000),
            )

            // then
            assertThat(payment.usedPoint).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.paidAmount).isEqualTo(Money.krw(10000))
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
            val paidAmount = Money.krw(5000) // 10000 - 3000 - 2000

            // when
            val payment = Payment.pending(
                userId = 1L,
                order = order,
                usedPoint = usedPoint,
                paidAmount = paidAmount,
                issuedCouponId = 100L,
                couponDiscount = couponDiscount,
            )

            // then
            assertThat(payment.totalAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.couponDiscount).isEqualTo(couponDiscount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.paidAmount).isEqualTo(paidAmount)
        }

        @DisplayName("사용 포인트가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when used point is negative`() {
            // given
            val order = createOrder()

            // when
            val exception = assertThrows<CoreException> {
                Payment.pending(
                    userId = 1L,
                    order = order,
                    usedPoint = Money.krw(-1000),
                    paidAmount = Money.krw(11000),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트는 0 이상이어야 합니다")
        }

        @DisplayName("카드 결제 금액이 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when paid amount is negative`() {
            // given
            val order = createOrder()

            // when
            val exception = assertThrows<CoreException> {
                Payment.pending(
                    userId = 1L,
                    order = order,
                    usedPoint = Money.krw(11000),
                    paidAmount = Money.krw(-1000),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("카드 결제 금액은 0 이상이어야 합니다")
        }

        @DisplayName("usedPoint + paidAmount + couponDiscount != totalAmount이면 예외가 발생한다")
        @Test
        fun `throws exception when payment amounts do not match total`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )

            // when
            val exception = assertThrows<CoreException> {
                Payment.pending(
                    userId = 1L,
                    order = order,
                    usedPoint = Money.krw(3000),
                    // 합계 8000 != 10000
                    paidAmount = Money.krw(5000),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("결제 금액이 주문 금액과 일치하지 않습니다")
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
            val payment = Payment.pending(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
                paidAmount = Money.ZERO_KRW,
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
            val payment = Payment.pending(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
                paidAmount = Money.ZERO_KRW,
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
            val payment = Payment.pending(
                userId = 1L,
                order = order,
                usedPoint = order.totalAmount,
                paidAmount = Money.ZERO_KRW,
            )

            // then
            assertThat(payment.version).isEqualTo(0L)
        }
    }

    private fun createPayment(
        userId: Long = 1L,
        order: Order = createOrder(),
    ): Payment {
        return Payment.pay(
            userId = userId,
            order = order,
            usedPoint = order.totalAmount,
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
