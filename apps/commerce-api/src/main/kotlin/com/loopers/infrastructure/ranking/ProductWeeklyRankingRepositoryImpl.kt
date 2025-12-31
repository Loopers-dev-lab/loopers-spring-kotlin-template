package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductWeeklyRanking
import com.loopers.domain.ranking.ProductWeeklyRankingRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ProductWeeklyRankingRepositoryImpl(
    private val productWeeklyRankingJpaRepository: ProductWeeklyRankingJpaRepository,
) : ProductWeeklyRankingRepository {

    override fun findByWeekRange(
        weekStart: LocalDate,
        weekEnd: LocalDate,
        pageable: Pageable,
    ): Page<ProductWeeklyRanking> {
        val sortedPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.by("ranking").ascending())
        return productWeeklyRankingJpaRepository.findByWeekStartAndWeekEnd(weekStart, weekEnd, sortedPageable)
    }
}
