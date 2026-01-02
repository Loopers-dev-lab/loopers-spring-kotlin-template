package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductDailyMetric
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Date
import java.sql.Timestamp
import java.time.ZonedDateTime

/**
 * DailyMetricWriter - ProductDailyMetric UPSERT
 *
 * - INSERT INTO ... ON DUPLICATE KEY UPDATE 패턴 사용
 * - (stat_date, product_id) 기준 unique constraint 활용
 * - Full recalculation 방식으로 멱등성 보장
 */
class DailyMetricWriter(
    private val jdbcTemplate: JdbcTemplate,
) : ItemWriter<ProductDailyMetric> {

    companion object {
        private const val BATCH_SIZE = 100

        private val UPSERT_SQL = """
            INSERT INTO product_daily_metric (stat_date, product_id, view_count, like_count, order_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                view_count = VALUES(view_count),
                like_count = VALUES(like_count),
                order_amount = VALUES(order_amount),
                updated_at = VALUES(updated_at)
        """.trimIndent()
    }

    override fun write(chunk: Chunk<out ProductDailyMetric>) {
        val items = chunk.items
        if (items.isEmpty()) return

        val now = Timestamp.from(ZonedDateTime.now().toInstant())
        jdbcTemplate.batchUpdate(
            UPSERT_SQL,
            items,
            BATCH_SIZE,
        ) { ps, metric ->
            ps.setDate(1, Date.valueOf(metric.statDate))
            ps.setLong(2, metric.productId)
            ps.setLong(3, metric.viewCount)
            ps.setLong(4, metric.likeCount)
            ps.setBigDecimal(5, metric.orderAmount)
            ps.setTimestamp(6, now)
            ps.setTimestamp(7, now)
        }
    }
}
