package com.loopers.infrastructure.point

import com.loopers.domain.point.PointAccount
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PointAccountJpaRepository : JpaRepository<PointAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointAccount p WHERE p.userId = :userId")
    fun findByUserIdWithLock(@Param("userId") userId: Long): PointAccount?
}
