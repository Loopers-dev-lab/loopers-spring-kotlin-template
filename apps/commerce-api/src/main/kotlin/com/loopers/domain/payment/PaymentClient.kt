package com.loopers.domain.payment

import com.loopers.domain.payment.dto.PaymentDto
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PaymentClient(private val paymentFeignClient: PaymentFeignClient) {

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentClient::class.java)
    }

    @Retry(name = "payment")
    @CircuitBreaker(name = "payment", fallbackMethod = "fallbackRequestPayment")
    fun requestPayment(
        request: PaymentDto.Request,
    ): ApiResponse<PaymentDto.Response> {
        logger.info("Requesting payment: orderKey=${request.orderId}")
        return paymentFeignClient.requestPayment(request)
    }

    fun fallbackRequestPayment(
        request: PaymentDto.Request,
        throwable: Throwable,
    ): ApiResponse<PaymentDto.Response> {
        logger.error("Payment circuit breaker activated for orderKey=${request.orderId}", throwable)
        throw CoreException(ErrorType.INTERNAL_ERROR, "결제 서비스 일시적 장애")
    }
}
