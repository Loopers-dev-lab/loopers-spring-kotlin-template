package com.loopers.application.point

import com.loopers.domain.point.PointService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointFacade(
    private val pointService: PointService,
) {
    @Transactional
    fun chargePoint(userId: Long, request: PointChargeRequest): PointInfo {
        val point = pointService.chargePoint(userId, request.toMoney())
        return PointInfo.from(point)
    }

    @Transactional(readOnly = true)
    fun getPoint(userId: Long): PointInfo {
        val point = pointService.getPoint(userId)
        return PointInfo.from(point)
    }
}
