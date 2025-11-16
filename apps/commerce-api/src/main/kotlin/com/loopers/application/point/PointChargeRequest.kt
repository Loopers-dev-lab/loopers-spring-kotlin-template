package com.loopers.application.point

import com.loopers.domain.order.Money
import com.loopers.domain.product.Currency
import java.math.BigDecimal

data class PointChargeRequest(
    val amount: BigDecimal,
    val currency: Currency = Currency.KRW,
) {
    fun toMoney(): Money {
        return Money(amount = amount, currency = currency)
    }
}
