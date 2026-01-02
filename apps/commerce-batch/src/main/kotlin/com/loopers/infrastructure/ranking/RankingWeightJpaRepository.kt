package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingWeight
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RankingWeightJpaRepository : JpaRepository<RankingWeight, Long> {

    @Query("SELECT rw FROM RankingWeight rw WHERE rw.deletedAt IS NULL ORDER BY rw.id DESC LIMIT 1")
    fun findLatest(): RankingWeight?
}
