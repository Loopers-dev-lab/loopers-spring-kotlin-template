package com.loopers.domain.payment

import com.loopers.domain.order.OrderService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PaymentRecoveryTransactionServiceTest {

    private lateinit var service: PaymentRecoveryTransactionService
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        paymentRepository = mockk()
        orderService = mockk()
        service = PaymentRecoveryTransactionService(
            orderService,
            paymentRepository,
        )
    }

    @DisplayName("결제 성공 시 주문 완료 및 재고 차감")
    @Test
    fun handlePaymentSuccess() {
        // given
        val orderId = 1L
        val paymentId = 100L

        val payment = mockk<Payment>(relaxed = true) {
            every { status } returns PaymentStatus.PENDING
            every { markAsSuccess() } just Runs
        }

        every { paymentRepository.findByIdOrThrow(paymentId) } returns payment
        every { paymentRepository.save(payment) } returns payment
        every { orderService.completeOrderWithPayment(any()) } just Runs

        // when
        service.handlePaymentSuccess(orderId, paymentId)

        // then
        verify(exactly = 1) { payment.markAsSuccess() }
        verify(exactly = 1) { paymentRepository.save(payment) }
        verify(exactly = 1) { orderService.completeOrderWithPayment(any()) }
    }

    @DisplayName("결제 실패 시 주문 취소 및 상태 업데이트")
    @Test
    fun handlePaymentFailure() {
        // given
        val orderId = 1L
        val paymentId = 100L
        val failureReason = "카드 한도 초과"

        val payment = mockk<Payment>(relaxed = true) {
            every { status } returns PaymentStatus.PENDING
            every { markAsFailed(any()) } just Runs
        }

        every { paymentRepository.findByIdOrThrow(paymentId) } returns payment
        every { paymentRepository.save(payment) } returns payment
        every { orderService.failOrder(orderId) } just Runs

        // when
        service.handlePaymentFailure(orderId, paymentId, failureReason)

        // then
        verify(exactly = 1) { orderService.failOrder(orderId) }
        verify(exactly = 1) { payment.markAsFailed(failureReason) }
        verify(exactly = 1) { paymentRepository.save(payment) }
    }

    @DisplayName("멱등성 보장 - 이미 SUCCESS 처리된 결제는 재처리하지 않음")
    @Test
    fun ignoresDuplicatePaymentSuccess() {
        // given
        val orderId = 1L
        val paymentId = 100L
        val failureReason = "카드 한도 초과"

        val payment = mockk<Payment>(relaxed = true) {
            every { status } returns PaymentStatus.PENDING
            every { markAsFailed(any()) } just Runs
        }

        every { paymentRepository.findByIdOrThrow(paymentId) } returns payment
        every { paymentRepository.save(payment) } returns payment
        every { orderService.failOrder(orderId) } just Runs

        // when
        service.handlePaymentFailure(orderId, paymentId, failureReason)

        // then
        verify(exactly = 1) { orderService.failOrder(orderId) }
        verify(exactly = 1) { payment.markAsFailed(failureReason) }
        verify(exactly = 1) { paymentRepository.save(payment) }
    }



}
