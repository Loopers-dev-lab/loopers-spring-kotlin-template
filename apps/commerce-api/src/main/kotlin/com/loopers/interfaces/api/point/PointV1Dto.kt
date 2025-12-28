package com.loopers.interfaces.api.point

import com.loopers.application.point.PointChargeRequest
import com.loopers.application.point.PointInfo
import com.loopers.domain.product.Currency
import java.math.BigDecimal
import java.time.LocalDateTime

class PointV1Dto {
    data class ChargeRequest(val amount: BigDecimal, val currency: Currency = Currency.KRW) {
        fun toCommand(): PointChargeRequest = PointChargeRequest(
                amount = amount,
                currency = currency,
            )
    }

    data class PointResponse(val userId: Long, val balance: BigDecimal, val currency: String, val updatedAt: LocalDateTime) {
        companion object {
            fun from(info: PointInfo): PointResponse = PointResponse(
                    userId = info.userId,
                    balance = info.balance,
                    currency = info.currency,
                    updatedAt = info.updatedAt,
                )
        }
    }
}
