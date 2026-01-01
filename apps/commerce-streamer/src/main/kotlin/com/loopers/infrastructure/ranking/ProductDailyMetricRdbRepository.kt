package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductDailyMetric
import com.loopers.domain.ranking.ProductDailyMetricRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.time.LocalDate

@Repository
class ProductDailyMetricRdbRepository(
    private val entityManager: EntityManager,
    private val productDailyMetricJpaRepository: ProductDailyMetricJpaRepository,
) : ProductDailyMetricRepository {

    /**
     * 일별 집계 데이터를 일괄 upsert
     *
     * - ON DUPLICATE KEY UPDATE를 사용하여 기존 값을 덮어씀
     * - 새 레코드는 INSERT, 기존 레코드는 UPDATE
     */
    @Transactional
    override fun upsertFromHourly(dailyMetrics: List<ProductDailyMetric>) {
        if (dailyMetrics.isEmpty()) return

        val sql = """
            INSERT INTO product_daily_metric
                (stat_date, product_id, view_count, like_count, order_amount, created_at, updated_at)
            VALUES
                (:statDate, :productId, :viewCount, :likeCount, :orderAmount, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                view_count = VALUES(view_count),
                like_count = VALUES(like_count),
                order_amount = VALUES(order_amount),
                updated_at = NOW()
        """.trimIndent()

        dailyMetrics.forEach { metric ->
            entityManager.createNativeQuery(sql)
                .setParameter("statDate", Date.valueOf(metric.statDate))
                .setParameter("productId", metric.productId)
                .setParameter("viewCount", metric.viewCount)
                .setParameter("likeCount", metric.likeCount)
                .setParameter("orderAmount", metric.orderAmount)
                .executeUpdate()
        }
    }

    /**
     * 특정 날짜의 모든 집계 데이터를 조회
     *
     * @param statDate 조회할 날짜
     * @return 해당 날짜의 모든 ProductDailyMetric 목록
     */
    @Transactional(readOnly = true)
    override fun findAllByStatDate(statDate: LocalDate): List<ProductDailyMetric> {
        return productDailyMetricJpaRepository.findAllByStatDate(statDate)
    }
}
