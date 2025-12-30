package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankDaily
import com.loopers.domain.ranking.ProductRankDailyRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

interface ProductRankDailyJpaRepository : JpaRepository<ProductRankDaily, Long> {
    @Transactional
    @Modifying
    @Query("DELETE FROM ProductRankDaily p WHERE p.rankingDate = :date")
    fun deleteByRankingDate(date: LocalDate)

    @Transactional
    @Modifying
    @Query("DELETE FROM ProductRankDaily p WHERE p.rankingDate >= :startDate AND p.rankingDate <= :endDate")
    fun deleteByRankingDateBetween(startDate: LocalDate, endDate: LocalDate)

    fun findByRankingDateBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ProductRankDaily>

    fun findByRankingDate(date: LocalDate): List<ProductRankDaily>
}

@Repository
class ProductRankDailyRepositoryImpl(private val jpaRepository: ProductRankDailyJpaRepository) : ProductRankDailyRepository {

    override fun save(entity: ProductRankDaily): ProductRankDaily = jpaRepository.save(entity)

    override fun saveAll(entities: List<ProductRankDaily>): List<ProductRankDaily> = jpaRepository.saveAll(entities)

    override fun deleteByRankingDate(date: LocalDate) {
        jpaRepository.deleteByRankingDate(date)
    }

    override fun deleteByYearMonth(yearMonth: java.time.YearMonth) {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        jpaRepository.deleteByRankingDateBetween(startDate, endDate)
    }

    override fun findByRankingDateBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ProductRankDaily> = jpaRepository.findByRankingDateBetween(startDate, endDate)

    override fun findByRankingDate(date: LocalDate): List<ProductRankDaily> = jpaRepository.findByRankingDate(date)
}
