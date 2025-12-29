package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface ProductRankWeeklyJpaRepository : JpaRepository<ProductRankWeekly, Long> {
    @Modifying
    @Transactional
    @Query("DELETE FROM ProductRankWeekly p WHERE p.yearWeek = :yearWeek")
    fun deleteByYearWeek(yearWeek: String)

    fun findByYearWeek(yearWeek: String): List<ProductRankWeekly>

    fun findByYearWeekOrderByRankAsc(yearWeek: String, pageable: Pageable): Page<ProductRankWeekly>

    @Query("SELECT p FROM ProductRankWeekly p WHERE p.yearWeek = :yearWeek ORDER BY p.rank")
    fun findTopByYearWeekOrderByRank(yearWeek: String, pageable: Pageable): List<ProductRankWeekly>
}

@Repository
class ProductRankWeeklyRepositoryImpl(private val jpaRepository: ProductRankWeeklyJpaRepository) :
    ProductRankWeeklyRepository {

    override fun save(entity: ProductRankWeekly): ProductRankWeekly = jpaRepository.save(entity)

    override fun saveAll(entities: List<ProductRankWeekly>): List<ProductRankWeekly> = jpaRepository.saveAll(entities)

    override fun deleteAll() {
        jpaRepository.deleteAll()
    }

    override fun deleteByYearWeek(yearWeek: String) {
        jpaRepository.deleteByYearWeek(yearWeek)
    }

    override fun findByYearWeek(yearWeek: String): List<ProductRankWeekly> = jpaRepository.findByYearWeek(yearWeek)

    override fun findByYearWeekOrderByRankAsc(
        yearWeek: String,
        pageable: Pageable,
    ): Page<ProductRankWeekly> = jpaRepository.findByYearWeekOrderByRankAsc(
        yearWeek,
        pageable,
    )

    override fun findTopByYearWeekOrderByRank(
        yearWeek: String,
        limit: Int,
    ): List<ProductRankWeekly> = jpaRepository.findTopByYearWeekOrderByRank(
        yearWeek,
        PageRequest.of(0, limit),
    )
}
