package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.ProductWeeklyMetricsAggregate
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.RowMapper
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 주간 상품 메트릭 Reader
 *
 * product_metrics에서 주간 범위의 날짜별 메트릭 데이터를 집계하여 반환
 * 각 날짜의 데이터를 product_id와 주간 종료일로부터의 일수로 집계
 */
@Configuration
class ProductWeeklyRankingReader {

    @Bean
    @StepScope
    fun weeklyRankingReader(
        dataSource: DataSource,
        @Value("#{jobParameters['weekStart']}") weekStart: String,
        @Value("#{jobParameters['weekEnd']}") weekEnd: String,
    ): JdbcPagingItemReader<ProductWeeklyMetricsAggregate> {
        /**
         * DATEDIFF(:weekEnd, metric_date) as days_from_end:  시간 가중치 계산용 (최근 날짜일수록 작은 값)
         */
        val selectClause = """
            SELECT
                product_id,
                DATEDIFF(:weekEnd, metric_date) as days_from_end, 
                SUM(view_count) as view_count,
                SUM(like_count) as like_count,
                SUM(sold_count) as sold_count
        """.trimIndent()

        val fromClause = "FROM product_metrics"

        val whereClause = "WHERE metric_date BETWEEN :weekStart AND :weekEnd"

        // product_id와 days_from_end로 정렬
        val sortKey = mapOf("product_id" to Order.ASCENDING, "days_from_end" to Order.ASCENDING)

        val rowMapper = RowMapper { rs, _ ->
            ProductWeeklyMetricsAggregate(
                productId = rs.getLong("product_id"),
                daysFromEnd = rs.getInt("days_from_end"),
                viewCount = rs.getLong("view_count"),
                likeCount = rs.getLong("like_count"),
                soldCount = rs.getLong("sold_count"),
            )
        }

        return JdbcPagingItemReaderBuilder<ProductWeeklyMetricsAggregate>()
            .name("productWeeklyRankingReader")
            .dataSource(dataSource)
            .queryProvider(
                MySqlPagingQueryProvider().apply {
                    setSelectClause(selectClause)
                    setFromClause(fromClause)
                    setWhereClause(whereClause)
                    groupClause = "product_id, days_from_end"
                    sortKeys = sortKey
                },
            )
            .parameterValues(
                mapOf(
                    "weekStart" to LocalDate.parse(weekStart),
                    "weekEnd" to LocalDate.parse(weekEnd),
                ),
            )
            .pageSize(100)
            .rowMapper(rowMapper)
            .saveState(true)
            .build()
    }
}
