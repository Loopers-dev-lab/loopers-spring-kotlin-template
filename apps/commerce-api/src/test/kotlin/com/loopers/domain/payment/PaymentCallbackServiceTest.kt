package com.loopers.domain.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.event.PaymentCompletedEvent
import com.loopers.domain.payment.event.PaymentFailedEvent
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class PaymentCallbackServiceTest {

    private lateinit var paymentCallbackService: PaymentCallbackService
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var eventPublisher: ApplicationEventPublisher

    @BeforeEach
    fun setUp() {
        paymentRepository = mockk()
        orderRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        paymentCallbackService = PaymentCallbackService(
            paymentRepository,
            orderRepository,
            eventPublisher
        )
    }

    @DisplayName("결제 성공 콜백을 처리할 수 있다")
    @Test
    fun handleSuccessCallback() {
        // given
        val transactionKey = "TR-20251205-001"
        val orderId = 1L
        val payment = Payment(
            orderId = orderId,
            amount = Money.of(10000),
            paymentMethod = PaymentMethod.CARD,
            transactionKey = transactionKey,
            cardType = "SAMSUNG",
            cardNumber = CardNumber.from("1234-5678-9012-3456")
        )
        val callback = PaymentCallbackDto(
            transactionKey = transactionKey,
            status = "SUCCESS",
            reason = null
        )

        val order = mockk<Order>(relaxed = true)
        every { order.id } returns orderId
        every { order.memberId } returns "testuser01"

        every { paymentRepository.findByTransactionKey(transactionKey) } returns payment
        every { orderRepository.findByIdOrThrow(orderId) } returns order

        val eventSlot = slot<Any>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        // when
        paymentCallbackService.handlePaymentCallback(callback)

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(eventSlot.captured).isInstanceOf(PaymentCompletedEvent::class.java)
        val capturedEvent = eventSlot.captured as PaymentCompletedEvent
        assertThat(capturedEvent.orderId).isEqualTo(orderId)
        assertThat(capturedEvent.memberId).isEqualTo("testuser01")
    }

    @DisplayName("결제 실패 콜백을 처리할 수 있다")
    @Test
    fun handleFailureCallback() {
        // given
        val transactionKey = "TR-20251205-001"
        val orderId = 1L
        val payment = Payment(
            orderId = orderId,
            amount = Money.of(10000),
            paymentMethod = PaymentMethod.CARD,
            transactionKey = transactionKey,
            cardType = "SAMSUNG",
            cardNumber = CardNumber.from("1234-5678-9012-3456")
        )
        val callback = PaymentCallbackDto(
            transactionKey = transactionKey,
            status = "FAILED",
            reason = "카드 한도 초과"
        )

        val order = mockk<Order>(relaxed = true)
        every { order.id } returns orderId
        every { order.memberId } returns "testuser01"

        every { paymentRepository.findByTransactionKey(transactionKey) } returns payment
        every { orderRepository.findByIdOrThrow(orderId) } returns order

        val eventSlot = slot<Any>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        // when
        paymentCallbackService.handlePaymentCallback(callback)

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.failureReason).isEqualTo("카드 한도 초과")
        assertThat(eventSlot.captured).isInstanceOf(PaymentFailedEvent::class.java)
        val capturedEvent = eventSlot.captured as PaymentFailedEvent
        assertThat(capturedEvent.orderId).isEqualTo(orderId)
        assertThat(capturedEvent.reason).isEqualTo("카드 한도 초과")
    }

    @DisplayName("중복 콜백 호출 시 두 번째 콜백은 무시된다 (멱등성)")
    @Test
    fun ignoresDuplicateCallback() {
        // given
        val transactionKey = "TR-20251205-001"
        val orderId = 1L
        val payment = Payment(
            orderId = orderId,
            amount = Money.of(10000),
            paymentMethod = PaymentMethod.CARD,
            transactionKey = transactionKey,
            cardType = "SAMSUNG",
            cardNumber = CardNumber.from("1234-5678-9012-3456")
        )
        payment.markAsSuccess() // 이미 처리됨

        val callback = PaymentCallbackDto(
            transactionKey = transactionKey,
            status = "SUCCESS",
            reason = null
        )

        every { paymentRepository.findByTransactionKey(transactionKey) } returns payment

        // when
        paymentCallbackService.handlePaymentCallback(callback)

        // then: 이벤트가 발행되지 않음 (이미 처리된 결제)
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @DisplayName("존재하지 않는 transactionKey로 콜백 호출 시 예외가 발생한다")
    @Test
    fun throwsExceptionWhenPaymentNotFound() {
        // given
        val callback = PaymentCallbackDto(
            transactionKey = "INVALID-KEY",
            status = "SUCCESS",
            reason = null
        )

        every { paymentRepository.findByTransactionKey("INVALID-KEY") } returns null

        // when & then
        val exception = assertThrows<CoreException> {
            paymentCallbackService.handlePaymentCallback(callback)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.PAYMENT_NOT_FOUND)
    }
}

