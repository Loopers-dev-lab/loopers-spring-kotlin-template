package com.loopers.infrastructure.point

import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PointAccountRdbRepository(
    private val pointAccountJpaRepository: PointAccountJpaRepository,
) : PointAccountRepository {

    @Transactional(readOnly = true)
    override fun findByUserId(userId: Long): PointAccount? {
        return pointAccountJpaRepository.findById(userId).orElse(null)
    }

    @Transactional
    override fun save(pointAccount: PointAccount): PointAccount {
        return pointAccountJpaRepository.save(pointAccount)
    }

    @Transactional
    override fun findByUserIdWithLock(userId: Long): PointAccount? {
        return pointAccountJpaRepository.findByUserIdWithLock(userId)
    }
}
