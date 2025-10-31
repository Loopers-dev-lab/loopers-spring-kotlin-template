package com.loopers.infrastructure.point

import com.loopers.domain.point.Point
import com.loopers.domain.user.UserId
import org.springframework.data.jpa.repository.JpaRepository

interface PointJpaRepository : JpaRepository<Point, Long> {
    fun findByUserId(userId: UserId): Point?
}
