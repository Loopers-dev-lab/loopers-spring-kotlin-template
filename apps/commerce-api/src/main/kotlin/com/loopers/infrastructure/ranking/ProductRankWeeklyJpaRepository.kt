package com.loopers.infrastructure.rank

import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface ProductRankWeeklyJpaRepository : JpaRepository<ProductRankWeekly, Long> {
    fun findByYearWeek(yearWeek: String): List<ProductRankWeekly>

    @Modifying
    @Query("DELETE FROM ProductRankWeekly p WHERE p.yearWeek = :yearWeek")
    fun deleteByYearWeek(yearWeek: String)
}

@Repository
class ProductRankWeeklyRepositoryImpl(private val jpaRepository: ProductRankWeeklyJpaRepository) : ProductRankWeeklyRepository {

    override fun findByYearWeek(yearWeek: String) = jpaRepository.findByYearWeek(yearWeek)

    override fun findTopByYearWeekOrderByRank(
        yearWeek: String,
        limit: Int,
    ) = jpaRepository.findByYearWeek(yearWeek)
        .sortedBy { it.rank }
        .take(limit)

    override fun saveAll(ranks: List<ProductRankWeekly>) = jpaRepository.saveAll(ranks)

    @Transactional
    override fun deleteByYearWeek(yearWeek: String) = jpaRepository.deleteByYearWeek(yearWeek)
}
