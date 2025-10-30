package com.loopers.infrastructure.point

import com.loopers.domain.point.PointModel
import org.springframework.data.jpa.repository.JpaRepository

interface PointJpaRepository : JpaRepository<PointModel, Long> {
    fun findByUserId(userId: Long): PointModel?
}
