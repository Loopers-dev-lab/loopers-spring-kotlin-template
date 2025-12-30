package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.dto.RankedProduct
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
 * productId별로 7일치 데이터를 집계하여 날짜별 감쇠 가중치를 적용한 점수 반환
 * - JdbcPagingItemReader 사용으로 메모리 효율적 처리
 * - productId별로 하나의 레코드만 반환하여 Chunk 경계 문제 해결
 */
@Configuration
class ProductWeeklyRankingReader {

    @Bean
    @StepScope
    fun weeklyRankingReader(
        dataSource: DataSource,
        @Value("#{jobParameters['weekStart']}") weekStart: String,
        @Value("#{jobParameters['weekEnd']}") weekEnd: String,
        @Value("\${batch.ranking.weights.view:1}") viewWeight: Double,
        @Value("\${batch.ranking.weights.like:5}") likeWeight: Double,
        @Value("\${batch.ranking.weights.sold:10}") soldWeight: Double,
    ): JdbcPagingItemReader<RankedProduct> {
        // SQL에서 날짜별 감쇠 가중치 적용하여 productId별 점수 집계
        val selectClause = """
            SELECT
                product_id,
                SUM(
                    (view_count * $viewWeight + like_count * $likeWeight + sold_count * $soldWeight) *
                    CASE
                        WHEN DATEDIFF(:weekEnd, metric_date) = 0 THEN 1.0
                        WHEN DATEDIFF(:weekEnd, metric_date) = 1 THEN 0.9
                        WHEN DATEDIFF(:weekEnd, metric_date) = 2 THEN 0.8
                        WHEN DATEDIFF(:weekEnd, metric_date) = 3 THEN 0.4
                        WHEN DATEDIFF(:weekEnd, metric_date) = 4 THEN 0.3
                        WHEN DATEDIFF(:weekEnd, metric_date) = 5 THEN 0.2
                        WHEN DATEDIFF(:weekEnd, metric_date) = 6 THEN 0.1
                        ELSE 0.0
                    END
                ) as final_score
        """.trimIndent()

        val fromClause = "FROM product_metrics"

        // 주간 범위는 job 파라미터로 주입된 날짜를 그대로 사용
        val whereClause = "WHERE metric_date BETWEEN :weekStart AND :weekEnd"

        val sortKey = mapOf("final_score" to Order.DESCENDING)

        val rowMapper = RowMapper { rs, _ ->
            RankedProduct(
                productId = rs.getLong("product_id"),
                finalScore = rs.getDouble("final_score"),
            )
        }

        return JdbcPagingItemReaderBuilder<RankedProduct>()
            .name("productWeeklyRankingReader")
            .dataSource(dataSource)
            .queryProvider(
                MySqlPagingQueryProvider().apply {
                    setSelectClause(selectClause)
                    setFromClause(fromClause)
                    setWhereClause(whereClause)
                    setGroupClause("product_id")
                    sortKeys = sortKey
                },
            )
            .parameterValues(
                mapOf(
                    // 문자열 파라미터를 LocalDate로 변환해 SQL 바인딩
                    "weekStart" to LocalDate.parse(weekStart),
                    "weekEnd" to LocalDate.parse(weekEnd),
                ),
            )
            .pageSize(100) // 100개씩 페이징
            .rowMapper(rowMapper)
            .saveState(true) // Job 재시작 지원
            .build()
    }
}
