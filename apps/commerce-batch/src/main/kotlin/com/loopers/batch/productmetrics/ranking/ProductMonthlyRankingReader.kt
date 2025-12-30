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
import java.time.YearMonth
import javax.sql.DataSource

/**
 * 월간 상품 메트릭 Reader
 *
 * product_metrics를 월 단위로 집계하여 productId별 점수를 반환
 */
@Configuration
class ProductMonthlyRankingReader {

    @Bean
    @StepScope
    fun monthlyRankingReader(
        dataSource: DataSource,
        @Value("#{jobParameters['yearMonth']}") yearMonth: String,
        @Value("\${batch.ranking.weights.view}") viewWeight: Double,
        @Value("\${batch.ranking.weights.like}") likeWeight: Double,
        @Value("\${batch.ranking.weights.sold}") soldWeight: Double,
    ): JdbcPagingItemReader<RankedProduct> {
        val month = YearMonth.parse(yearMonth)
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()

        // 월 범위 내 일자별 메트릭을 합산
        val selectClause = """
            SELECT
                product_id,
                SUM(
                    view_count * $viewWeight +
                    like_count * $likeWeight +
                    sold_count * $soldWeight
                ) as final_score
        """.trimIndent()

        val fromClause = "FROM product_metrics"

        val whereClause = "WHERE metric_date BETWEEN :monthStart AND :monthEnd"

        val sortKey = mapOf("final_score" to Order.DESCENDING)

        val rowMapper = RowMapper { rs, _ ->
            RankedProduct(
                productId = rs.getLong("product_id"),
                finalScore = rs.getDouble("final_score"),
            )
        }

        return JdbcPagingItemReaderBuilder<RankedProduct>()
            .name("productMonthlyRankingReader")
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
