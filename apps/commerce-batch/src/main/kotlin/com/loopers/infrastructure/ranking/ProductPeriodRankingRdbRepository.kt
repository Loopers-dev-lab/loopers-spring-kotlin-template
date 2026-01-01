package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.MvProductRankMonthly
import com.loopers.domain.ranking.MvProductRankWeekly
import com.loopers.domain.ranking.ProductPeriodRankingRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * ProductPeriodRankingRepository JdbcTemplate 구현체
 *
 * - 배치 INSERT/DELETE 작업에 JdbcTemplate 사용
 * - 주간/월간 랭킹 데이터 저장 및 삭제
 */
@Repository
class ProductPeriodRankingRdbRepository(
    private val jdbcTemplate: JdbcTemplate,
) : ProductPeriodRankingRepository {

    /**
     * 특정 날짜의 주간 랭킹을 삭제한다
     */
    @Transactional
    override fun deleteWeeklyByBaseDate(baseDate: LocalDate): Int {
        return jdbcTemplate.update(DELETE_WEEKLY_SQL, Date.valueOf(baseDate))
    }

    /**
     * 특정 날짜의 월간 랭킹을 삭제한다
     */
    @Transactional
    override fun deleteMonthlyByBaseDate(baseDate: LocalDate): Int {
        return jdbcTemplate.update(DELETE_MONTHLY_SQL, Date.valueOf(baseDate))
    }

    /**
     * 주간 랭킹 데이터를 일괄 저장한다
     */
    @Transactional
    override fun saveAllWeekly(rankings: List<MvProductRankWeekly>) {
        if (rankings.isEmpty()) return

        val now = Timestamp.from(ZonedDateTime.now().toInstant())
        jdbcTemplate.batchUpdate(
            INSERT_WEEKLY_SQL,
            rankings,
            BATCH_SIZE,
        ) { ps, ranking ->
            ps.setDate(1, Date.valueOf(ranking.baseDate))
            ps.setInt(2, ranking.rank)
            ps.setLong(3, ranking.productId)
            ps.setBigDecimal(4, ranking.score)
            ps.setTimestamp(5, now)
            ps.setTimestamp(6, now)
        }
    }

    /**
     * 월간 랭킹 데이터를 일괄 저장한다
     */
    @Transactional
    override fun saveAllMonthly(rankings: List<MvProductRankMonthly>) {
        if (rankings.isEmpty()) return

        val now = Timestamp.from(ZonedDateTime.now().toInstant())
        jdbcTemplate.batchUpdate(
            INSERT_MONTHLY_SQL,
            rankings,
            BATCH_SIZE,
        ) { ps, ranking ->
            ps.setDate(1, Date.valueOf(ranking.baseDate))
            ps.setInt(2, ranking.rank)
            ps.setLong(3, ranking.productId)
            ps.setBigDecimal(4, ranking.score)
            ps.setTimestamp(5, now)
            ps.setTimestamp(6, now)
        }
    }

    companion object {
        private const val BATCH_SIZE = 100

        private val DELETE_WEEKLY_SQL = """
            DELETE FROM mv_product_rank_weekly WHERE base_date = ?
        """.trimIndent()

        private val DELETE_MONTHLY_SQL = """
            DELETE FROM mv_product_rank_monthly WHERE base_date = ?
        """.trimIndent()

        private val INSERT_WEEKLY_SQL = """
            INSERT INTO mv_product_rank_weekly (base_date, `rank`, product_id, score, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        private val INSERT_MONTHLY_SQL = """
            INSERT INTO mv_product_rank_monthly (base_date, `rank`, product_id, score, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }
}
