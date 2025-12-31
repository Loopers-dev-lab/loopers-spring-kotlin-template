package com.loopers.batch.productmetrics.ranking

import com.loopers.domain.ranking.ProductWeeklyRanking
import com.loopers.domain.ranking.ProductWeeklyRankingRepository
import com.loopers.domain.ranking.dto.RankedProduct
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.annotation.AfterStep
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * 주간 상품 랭킹 Writer
 *
 * RankedProduct를 받아서 임시 테이블에 누적하고, AfterStep에서 productId별로 점수를 합산하여 Top 100을 저장
 *
 * 메모리 사용량을 최소화하기 위해 임시 테이블(temp_weekly_ranking)을 활용:
 * 1. BeforeStep: 임시 테이블 생성 및 기존 데이터 삭제
 * 2. write(): 청크 단위로 임시 테이블에 INSERT ... ON DUPLICATE KEY UPDATE
 * 3. AfterStep: 임시 테이블에서 Top 100 추출 후 저장
 */
@Component
@StepScope
class ProductWeeklyRankingWriter(
    private val jdbcTemplate: JdbcTemplate,
    private val repository: ProductWeeklyRankingRepository,
    private val transactionTemplate: TransactionTemplate,
    @Value("#{jobParameters['weekStart']}") private val weekStart: String,
    @Value("#{jobParameters['weekEnd']}") private val weekEnd: String,
) : ItemWriter<RankedProduct> {

    private val log = LoggerFactory.getLogger(ProductWeeklyRankingWriter::class.java)

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        // 임시 테이블 생성 (이미 존재하면 재사용)
        jdbcTemplate.execute(
            """
            CREATE TEMPORARY TABLE IF NOT EXISTS temp_weekly_ranking (
                product_id BIGINT NOT NULL,
                score DOUBLE NOT NULL DEFAULT 0,
                PRIMARY KEY (product_id)
            )
            """.trimIndent(),
        )

        // 임시 테이블 초기화 (이전 실행 데이터 제거)
        jdbcTemplate.execute("TRUNCATE TABLE temp_weekly_ranking")

        log.info("Initialized temp_weekly_ranking table")
    }

    override fun write(chunk: Chunk<out RankedProduct>) {
        if (chunk.isEmpty) {
            return
        }

        // 임시 테이블에 청크 데이터 삽입 (중복 시 점수 합산)
        val sql = """
            INSERT INTO temp_weekly_ranking (product_id, score)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE score = score + VALUES(score)
        """.trimIndent()

        jdbcTemplate.batchUpdate(
            sql,
            chunk.items,
            chunk.size(),
        ) { ps, item ->
            ps.setLong(1, item.productId)
            ps.setDouble(2, item.finalScore)
        }

        log.debug("Inserted {} products into temp_weekly_ranking", chunk.size())
    }

    @AfterStep
    fun afterStep(stepExecution: StepExecution) {
        if (stepExecution.exitStatus.exitCode != "COMPLETED") {
            log.warn("Step did not complete successfully. Skipping weekly ranking save.")
            return
        }

        val start = LocalDate.parse(weekStart)
        val end = LocalDate.parse(weekEnd)

        // 임시 테이블에서 총 상품 수 확인
        val totalProducts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM temp_weekly_ranking",
            Long::class.java,
        ) ?: 0L

        if (totalProducts == 0L) {
            log.warn("No ranked products to save")
            return
        }

        // 임시 테이블에서 Top 100 추출 (점수 내림차순)
        val top100 = jdbcTemplate.query(
            """
            SELECT
                product_id,
                score,
                ROW_NUMBER() OVER (ORDER BY score DESC) as ranking
            FROM temp_weekly_ranking
            ORDER BY score DESC
            LIMIT 100
            """.trimIndent(),
        ) { rs, _ ->
            ProductWeeklyRanking.create(
                ranking = rs.getInt("ranking"),
                productId = rs.getLong("product_id"),
                score = rs.getDouble("score"),
                weekStart = start,
                weekEnd = end,
            )
        }

        // 트랜잭션 내에서 삭제 및 저장 작업 수행
        transactionTemplate.execute {
            // 동일 주차 데이터는 삭제 후 재적재하여 멱등성 확보
            repository.deleteByWeekStartAndWeekEnd(start, end)

            // Top 100 저장
            repository.saveAll(top100)
        }

        log.info(
            "Saved Top {} weekly rankings for week {} to {} (total products: {})",
            top100.size,
            start,
            end,
            totalProducts,
        )

        // 임시 테이블 정리
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS temp_weekly_ranking")
        log.debug("Dropped temp_weekly_ranking table")
    }
}
