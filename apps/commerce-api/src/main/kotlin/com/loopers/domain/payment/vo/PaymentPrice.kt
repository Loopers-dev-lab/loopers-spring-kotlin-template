package com.loopers.domain.payment.vo

import com.loopers.domain.payment.policy.PaymentValidator
import java.math.BigDecimal

@JvmInline
value class PaymentPrice(
    val value: BigDecimal,
) {
    init {
        PaymentValidator.validatePrice(value)
    }
}
