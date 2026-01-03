package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankMonthlyRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface ProductRankMonthlyJpaRepository : JpaRepository<ProductRankMonthly, Long> {
    @Modifying
    @Transactional
    @Query("DELETE FROM ProductRankMonthly p WHERE p.yearMonth = :yearMonth")
    fun deleteByYearMonth(yearMonth: String)

    fun findByYearMonth(yearMonth: String): List<ProductRankMonthly>

    fun findByYearMonthOrderByRankAsc(yearMonth: String, pageable: Pageable): Page<ProductRankMonthly>

    @Query("SELECT p FROM ProductRankMonthly p WHERE p.yearMonth = :yearMonth ORDER BY p.rank")
    fun findTopByYearMonthOrderByRank(yearMonth: String, pageable: Pageable): List<ProductRankMonthly>
}

@Repository
class ProductRankMonthlyRepositoryImpl(private val jpaRepository: ProductRankMonthlyJpaRepository) :
    ProductRankMonthlyRepository {

    override fun save(entity: ProductRankMonthly): ProductRankMonthly = jpaRepository.save(entity)

    override fun saveAll(entities: List<ProductRankMonthly>): List<ProductRankMonthly> = jpaRepository.saveAll(entities)

    override fun deleteAll() {
        jpaRepository.deleteAll()
    }

    override fun deleteByYearMonth(yearMonth: String) {
        jpaRepository.deleteByYearMonth(yearMonth)
    }

    override fun findByYearMonth(yearMonth: String): List<ProductRankMonthly> = jpaRepository.findByYearMonth(yearMonth)

    override fun findByYearMonthOrderByRankAsc(
        yearMonth: String,
        pageable: Pageable,
    ): Page<ProductRankMonthly> = jpaRepository.findByYearMonthOrderByRankAsc(
        yearMonth,
        pageable,
    )

    override fun findTopByYearMonthOrderByRank(
        yearMonth: String,
        limit: Int,
    ): List<ProductRankMonthly> = jpaRepository.findTopByYearMonthOrderByRank(
        yearMonth,
        PageRequest.of(0, limit),
    )
}
