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
 * 주간 랭킹 데이터를 월 단위로 합산하여 productId별 점수를 반환
 */
@Configuration
class ProductMonthlyRankingReader {

    @Bean
    @StepScope
    fun monthlyRankingReader(
        dataSource: DataSource,
        @Value("#{jobParameters['yearMonth']}") yearMonth: String,
    ): JdbcPagingItemReader<RankedProduct> {
        val month = YearMonth.parse(yearMonth)
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()

        // 주간 집계 MV를 월 범위로 덮어 합산
        val selectClause = """
            SELECT
                product_id,
                SUM(score) as final_score
        """.trimIndent()

        val fromClause = "FROM mv_product_rank_weekly"

        val whereClause = """
            WHERE week_start <= :monthEnd
              AND week_end >= :monthStart
            GROUP BY product_id
        """.trimIndent()

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
            .pageSize(1000)
            .rowMapper(rowMapper)
            .saveState(true)
            .build()
    }
}
