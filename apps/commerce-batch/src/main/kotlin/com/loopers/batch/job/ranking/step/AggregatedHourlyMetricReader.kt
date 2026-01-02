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
 * AggregatedHourlyMetricReader - 시간별 메트릭을 일별로 집계하여 조회
 *
 * - targetDate의 product_hourly_metric 데이터를 product_id별로 GROUP BY
 * - SUM(view_count), SUM(like_count), SUM(order_amount)를 DB에서 수행
 * - ProductDailyMetric 형태로 반환
 * - OFFSET 기반 페이징 (Page Size: 1000)
 */
class AggregatedHourlyMetricReader(
    dataSource: DataSource,
    targetDate: LocalDate,
) : JdbcPagingItemReader<ProductDailyMetric>() {

    companion object {
        private const val PAGE_SIZE = 1000
    }

    init {
        val queryProvider = MySqlPagingQueryProvider().apply {
            setSelectClause(
                """
                SELECT
                    DATE(stat_hour) as stat_date,
                    product_id,
                    SUM(view_count) as view_count,
                    SUM(like_count) as like_count,
                    SUM(order_amount) as order_amount
                """.trimIndent(),
            )
            setFromClause("FROM product_hourly_metric")
            setWhereClause("WHERE DATE(stat_hour) = :targetDate")
            setGroupClause("GROUP BY DATE(stat_hour), product_id")
            setSortKeys(
                mapOf(
                    "product_id" to Order.ASCENDING,
                ),
            )
        }

        setName("aggregatedHourlyMetricReader")
        setDataSource(dataSource)
        setPageSize(PAGE_SIZE)
        setQueryProvider(queryProvider)
        setParameterValues(
            mapOf(
                "targetDate" to Date.valueOf(targetDate),
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
