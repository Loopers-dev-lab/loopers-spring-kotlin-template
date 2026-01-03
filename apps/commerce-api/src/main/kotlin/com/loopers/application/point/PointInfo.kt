package com.loopers.application.point

import com.loopers.domain.point.Point
import java.math.BigDecimal
import java.time.LocalDateTime

data class PointInfo(val userId: Long, val balance: BigDecimal, val currency: String, val updatedAt: LocalDateTime) {
    companion object {
        fun from(point: Point): PointInfo = PointInfo(
            userId = point.userId,
            balance = point.balance.amount,
            currency = point.balance.currency.name,
            updatedAt = point.updatedAt,
        )
    }
}
