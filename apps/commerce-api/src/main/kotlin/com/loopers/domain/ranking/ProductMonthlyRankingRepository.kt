package com.loopers.domain.ranking

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.YearMonth

interface ProductMonthlyRankingRepository {
    fun findByMonthPeriod(monthPeriod: YearMonth, pageable: Pageable): Page<ProductMonthlyRanking>
}
