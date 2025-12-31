package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.ProductMonthlyMetricsAggregate
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.RowMapper
import java.time.YearMonth
import javax.sql.DataSource

/**
 * 월간 상품 메트릭 Reader
 *
 * product_metrics를 월 단위로 집계하여 productId별 원본 메트릭을 반환
 */
@Configuration
class ProductMonthlyRankingReader {

    @Bean
    @StepScope
    fun monthlyRankingReader(
        dataSource: DataSource,
        @Value("#{jobParameters['yearMonth']}") yearMonth: String,
    ): JdbcPagingItemReader<ProductMonthlyMetricsAggregate> {
        val month = YearMonth.parse(yearMonth)
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()

        // 월 범위 내 일자별 메트릭을 합산 (원본 값만)
        val selectClause = """
            SELECT
                product_id,
                SUM(view_count) as total_view_count,
                SUM(like_count) as total_like_count,
                SUM(sold_count) as total_sold_count
        """.trimIndent()

        val fromClause = "FROM product_metrics"

        val whereClause = "WHERE metric_date BETWEEN :monthStart AND :monthEnd"

        // 임시 정렬 키 (Processor에서 점수 계산 후 재정렬됨)
        val sortKey = mapOf("product_id" to Order.ASCENDING)

        val rowMapper = RowMapper { rs, _ ->
            ProductMonthlyMetricsAggregate(
                productId = rs.getLong("product_id"),
                totalViewCount = rs.getLong("total_view_count"),
                totalLikeCount = rs.getLong("total_like_count"),
                totalSoldCount = rs.getLong("total_sold_count"),
            )
        }

        return JdbcPagingItemReaderBuilder<ProductMonthlyMetricsAggregate>()
            .name("productMonthlyRankingReader")
            .dataSource(dataSource)
            .queryProvider(
                MySqlPagingQueryProvider().apply {
                    setSelectClause(selectClause)
                    setFromClause(fromClause)
                    setWhereClause(whereClause)
                    groupClause = "product_id"
                    sortKeys = sortKey
                },
            )
            .parameterValues(
                mapOf(
                    // 월 경계는 LocalDate로 바인딩
                    "monthStart" to monthStart,
                    "monthEnd" to monthEnd,
                ),
            )
            .pageSize(100)
            .rowMapper(rowMapper)
            .saveState(true)
            .build()
    }
}
