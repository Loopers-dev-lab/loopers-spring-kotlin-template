package com.loopers.domain.payment

import com.loopers.domain.order.Order
import com.loopers.domain.payment.strategy.PgStrategy
import com.loopers.infrastructure.pg.PgDto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val pgStrategies: List<PgStrategy>,
    @Value("\${payment.callback.url}") private val callbackUrl: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "paymentFallback")
    @Transactional
    fun requestCardPayment(
        order: Order,
        userId: String,
        cardType: String,
        cardNo: String,
        amount: Long? = null
    ): Payment {
        val paymentAmount = amount ?: order.finalAmount.amount

        logger.info("카드 결제 요청: orderId=${order.id}, amount=$paymentAmount")

        val pgStrategy = pgStrategies.firstOrNull { it.supports(PaymentMethod.CARD) }
            ?: throw CoreException(ErrorType.PAYMENT_UNAVAILABLE, "사용 가능한 PG가 없습니다.")

        val pgRequest = PgDto.PaymentRequest(
            orderId = "ORDER${order.id.toString().padStart(6, '0')}", // ORDER000001 형식
            cardType = PgDto.CardTypeDto.from(cardType),
            cardNo = cardNo,
            amount = paymentAmount,
            callbackUrl = callbackUrl
        )

        val pgResponse = pgStrategy.requestPayment(userId, pgRequest)

        logger.info("PG 응답 수신: transactionKey=${pgResponse.transactionKey}, status=${pgResponse.status}")

        val payment = Payment(
            orderId = order.id,
            amount = com.loopers.domain.shared.Money(paymentAmount),
            paymentMethod = PaymentMethod.CARD,
            transactionKey = pgResponse.transactionKey,
            cardType = cardType,
            cardNumber = CardNumber.from(cardNo)
        )

        return paymentRepository.save(payment)
    }

    private fun paymentFallback(
        order: Order,
        userId: String,
        cardType: String,
        cardNo: String,
        amount: Long?,
        ex: Exception
    ): Payment {
        val paymentAmount = amount ?: order.finalAmount.amount

        logger.warn("결제 폴백 실행 - PENDING 상태로 저장: orderId=${order.id}, amount=$paymentAmount, reason=${ex.message}")

        val payment = Payment(
            orderId = order.id,
            amount = com.loopers.domain.shared.Money(paymentAmount),
            paymentMethod = PaymentMethod.CARD,
            cardType = cardType,
            cardNumber = CardNumber.from(cardNo)
        )

        return paymentRepository.save(payment)
    }

}
