package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductHourlyMetricRepository
import com.loopers.domain.ranking.ProductHourlyMetricRow
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Repository
class ProductHourlyMetricRdbRepository(
    private val entityManager: EntityManager,
) : ProductHourlyMetricRepository {

    /**
     * 배치로 집계 데이터를 누적 저장
     *
     * - ON DUPLICATE KEY UPDATE를 사용하여 기존 값에 누적
     * - 새 레코드는 INSERT, 기존 레코드는 UPDATE
     */
    @Transactional
    override fun batchAccumulateCounts(rows: List<ProductHourlyMetricRow>) {
        if (rows.isEmpty()) return

        val sql = """
            INSERT INTO product_hourly_metric
                (stat_hour, product_id, view_count, like_count, order_count, order_amount, created_at, updated_at)
            VALUES
                (:statHour, :productId, :viewCount, :likeCount, :orderCount, :orderAmount, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                view_count = view_count + VALUES(view_count),
                like_count = like_count + VALUES(like_count),
                order_count = order_count + VALUES(order_count),
                order_amount = order_amount + VALUES(order_amount),
                updated_at = NOW()
        """.trimIndent()

        rows.forEach { row ->
            entityManager.createNativeQuery(sql)
                .setParameter("statHour", Timestamp.from(row.statHour))
                .setParameter("productId", row.productId)
                .setParameter("viewCount", row.viewCount)
                .setParameter("likeCount", row.likeCount)
                .setParameter("orderCount", row.orderCount)
                .setParameter("orderAmount", row.orderAmount)
                .executeUpdate()
        }
    }
}
