package com.loopers.application.point

import com.loopers.domain.point.PointCommand
import com.loopers.domain.point.PointResult
import com.loopers.domain.point.PointService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointFacade(
    private val pointService: PointService,
) {

    @Transactional
    fun charge(command: PointCommand.Charge): PointResult {
        return pointService.charge(command).let { PointResult.from(it) }
    }

    @Transactional(readOnly = true)
    fun getBy(userId: String): PointResult {
        return pointService.getBy(userId)
            ?.let { PointResult.from(it) }
            ?: PointResult.init(userId)
    }
}
