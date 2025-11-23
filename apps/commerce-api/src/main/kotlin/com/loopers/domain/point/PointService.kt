package com.loopers.domain.point

import com.loopers.domain.common.vo.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class PointService(private val pointRepository: PointRepository) {
    fun getPointByUserId(userId: Long): PointModel? = pointRepository.findByUserId(userId)

    fun createPoint(userId: Long, amount: BigDecimal): PointModel {
        val point = PointModel(userId, Money(amount))
        return pointRepository.save(point)
    }

    @Transactional
    fun charge(userId: Long, amount: BigDecimal): PointModel =
        pointRepository.getUserByUserIdWithPessimisticLock(userId).also { it.charge(Money(amount)) }

    @Transactional
    fun pay(userId: Long, amount: BigDecimal): PointModel =
        pointRepository.getUserByUserIdWithPessimisticLock(userId).also { it.pay(Money(amount)) }
}
