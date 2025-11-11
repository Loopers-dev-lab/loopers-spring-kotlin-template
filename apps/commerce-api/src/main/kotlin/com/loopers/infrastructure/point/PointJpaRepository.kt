package com.loopers.infrastructure.point

import com.loopers.domain.point.Point
import org.springframework.data.jpa.repository.JpaRepository

interface PointJpaRepository : JpaRepository<Point, Long> {
    fun findByUserId(userId: Long): Point?
}
