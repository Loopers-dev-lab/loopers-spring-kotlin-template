package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankWeekly
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankWeeklyJpaRepository : JpaRepository<ProductRankWeekly, Long> {

    fun findByRefProductIdAndDateTime(refProductId: Long, dateTime: String): ProductRankWeekly?

    fun findByDateTimeOrderByScoreDesc(dateTime: String): List<ProductRankWeekly>

    fun deleteByDateTime(dateTime: String)
}

