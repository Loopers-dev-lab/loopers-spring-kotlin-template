package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductDailyMetric
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider
import java.math.BigDecimal
import java.sql.Date
import java.time.LocalDate
import javax.sql.DataSource

/**
 * MetricAggregationReader - ProductDailyMetric 페이징 조회
 *
 * - baseDate 기준 최근 windowDays일 데이터 조회
 * - OFFSET 기반 페이징 (Page Size: 1000)
 * - stat_date, product_id 순으로 정렬
 */
class MetricAggregationReader(
    dataSource: DataSource,
    baseDate: LocalDate,
    windowDays: Long,
) : JdbcPagingItemReader<ProductDailyMetric>() {

    companion object {
        private const val PAGE_SIZE = 1000
    }

    init {
        val endDate = baseDate.minusDays(1) // baseDate - 1 (당일 데이터 미포함)
        val startDate = baseDate.minusDays(windowDays) // baseDate - windowDays

        val queryProvider = MySqlPagingQueryProvider().apply {
            setSelectClause("SELECT id, stat_date, product_id, view_count, like_count, order_amount, created_at, updated_at")
            setFromClause("FROM product_daily_metric")
            setWhereClause("WHERE stat_date BETWEEN :startDate AND :endDate")
            setSortKeys(
                mapOf(
                    "stat_date" to Order.ASCENDING,
                    "product_id" to Order.ASCENDING,
                ),
            )
        }

        setName("metricAggregationReader")
        setDataSource(dataSource)
        setPageSize(PAGE_SIZE)
        setQueryProvider(queryProvider)
        setParameterValues(
            mapOf(
                "startDate" to Date.valueOf(startDate),
                "endDate" to Date.valueOf(endDate),
            ),
        )
        setRowMapper { rs, _ ->
            ProductDailyMetric(
                statDate = rs.getDate("stat_date").toLocalDate(),
                productId = rs.getLong("product_id"),
                viewCount = rs.getLong("view_count"),
                likeCount = rs.getLong("like_count"),
                orderAmount = rs.getBigDecimal("order_amount") ?: BigDecimal.ZERO,
            )
        }

        // JdbcPagingItemReader requires afterPropertiesSet() to be called
        afterPropertiesSet()
    }
}
