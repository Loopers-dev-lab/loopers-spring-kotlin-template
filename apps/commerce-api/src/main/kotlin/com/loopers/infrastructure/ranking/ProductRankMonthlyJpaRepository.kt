package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankMonthlyRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface ProductRankMonthlyJpaRepository : JpaRepository<ProductRankMonthly, Long> {
    fun findByYearMonth(yearMonth: String): List<ProductRankMonthly>

    fun findByYearMonth(yearMonth: String, pageable: Pageable): List<ProductRankMonthly>

    @Modifying
    @Query("DELETE FROM ProductRankMonthly p WHERE p.yearMonth = :yearMonth")
    fun deleteByYearMonth(yearMonth: String)
}

@Repository
class ProductRankMonthlyRepositoryImpl(private val jpaRepository: ProductRankMonthlyJpaRepository) :
    ProductRankMonthlyRepository {

    override fun findByYearMonth(yearMonth: String) = jpaRepository.findByYearMonth(yearMonth)

    override fun findTopByYearMonthOrderByRank(
        yearMonth: String,
        limit: Int,
    ) = jpaRepository.findByYearMonth(yearMonth, PageRequest.of(0, limit, Sort.by("rank")))

    override fun saveAll(ranks: List<ProductRankMonthly>) = jpaRepository.saveAll(ranks)

    @Transactional
    override fun deleteByYearMonth(yearMonth: String) = jpaRepository.deleteByYearMonth(yearMonth)
}
