package com.loopers.domain.payment.strategy

import com.loopers.domain.payment.entity.Payment
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentStrategyRegistry(strategies: List<PaymentStrategy>) {
    private val map = strategies.associateBy { it.supports() }
    fun of(method: Payment.Method): PaymentStrategy =
        map[method] ?: throw CoreException(ErrorType.INTERNAL_ERROR, "결제 수단에 대한 결제 방법이 없습니다 (method = $method)")
}
