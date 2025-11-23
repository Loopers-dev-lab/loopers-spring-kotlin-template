package com.loopers.infrastructure.point

import com.loopers.domain.point.PointModel
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface PointJpaRepository : JpaRepository<PointModel, Long> {
    fun findByRefUserId(userId: Long): PointModel?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointModel p WHERE p.refUserId = :userId")
    fun getUserByUserIdWithPessimisticLock(userId: Long): PointModel?
}
