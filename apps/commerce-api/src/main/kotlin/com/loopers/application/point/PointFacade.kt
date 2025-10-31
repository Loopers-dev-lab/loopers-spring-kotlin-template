package com.loopers.application.point

import com.loopers.domain.point.Money
import com.loopers.domain.point.PointService
import org.springframework.stereotype.Component

@Component
class PointFacade(
    private val pointService: PointService,
) {
    fun charge(userId: Long, amount: Money): PointV1Info.Charge {
        return pointService.charge(userId, amount)
            .aggregateBalance()
            .let { PointV1Info.Charge.from(it) }
    }
}
