package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductHourlyMetric
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

/**
 * HourlyMetricReader - ProductHourlyMetric 페이징 조회
 *
 * - baseDateTime 기준 현재 시간과 이전 시간 데이터 조회
 * - OFFSET 기반 페이징 (Page Size: 1000)
 * - stat_hour, product_id 순으로 정렬
 */
class HourlyMetricReader(
    dataSource: DataSource,
    baseDateTime: Instant,
) : JdbcPagingItemReader<ProductHourlyMetric>() {

    companion object {
        private const val PAGE_SIZE = 1000
        private val ZONE_ID = ZoneId.of("Asia/Seoul")
    }

    init {
        // 현재 시간 버킷 (baseDateTime을 시간 단위로 truncate)
        val currentHour = baseDateTime.truncatedTo(ChronoUnit.HOURS)
        // 이전 시간 버킷
        val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

        val queryProvider = MySqlPagingQueryProvider().apply {
            setSelectClause("SELECT id, stat_hour, product_id, view_count, like_count, order_amount, created_at, updated_at")
            setFromClause("FROM product_hourly_metric")
            setWhereClause("WHERE stat_hour IN (:currentHour, :previousHour)")
            setSortKeys(
                mapOf(
                    "stat_hour" to Order.ASCENDING,
                    "product_id" to Order.ASCENDING,
                ),
            )
        }

        setName("hourlyMetricReader")
        setDataSource(dataSource)
        setPageSize(PAGE_SIZE)
        setQueryProvider(queryProvider)
        setParameterValues(
            mapOf(
                "currentHour" to Timestamp.from(currentHour),
                "previousHour" to Timestamp.from(previousHour),
            ),
        )
        setRowMapper { rs, _ ->
            ProductHourlyMetric.create(
                statHour = rs.getTimestamp("stat_hour").toInstant(),
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
