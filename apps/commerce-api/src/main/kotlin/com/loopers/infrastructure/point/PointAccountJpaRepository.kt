package com.loopers.infrastructure.point

import com.loopers.domain.point.PointAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointAccountJpaRepository : JpaRepository<PointAccount, Long>
