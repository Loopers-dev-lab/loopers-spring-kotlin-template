package com.loopers.application.point

import com.loopers.domain.point.PointResult
import com.loopers.domain.point.PointService
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PointFacade(
    private val pointService: PointService,
) {
    fun findByUserId(userId: Long): PointResult.PointInfoResult? {
        return pointService.findByUserId(userId)
            ?.let { PointResult.PointInfoResult.from(it) }
    }

    fun chargePoint(userId: Long, amount: BigDecimal): PointResult.PointInfoResult {
        return pointService.chargePoint(userId, amount)
            .let { PointResult.PointInfoResult.from(it) }
    }
}
