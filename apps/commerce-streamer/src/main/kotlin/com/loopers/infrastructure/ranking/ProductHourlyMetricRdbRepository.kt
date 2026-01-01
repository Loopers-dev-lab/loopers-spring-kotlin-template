package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductHourlyMetric
import com.loopers.domain.ranking.ProductHourlyMetricRepository
import com.loopers.domain.ranking.ProductHourlyMetricRow
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Repository
class ProductHourlyMetricRdbRepository(
    private val entityManager: EntityManager,
    private val productHourlyMetricJpaRepository: ProductHourlyMetricJpaRepository,
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
                (stat_hour, product_id, view_count, like_count, order_amount, created_at, updated_at)
            VALUES
                (:statHour, :productId, :viewCount, :likeCount, :orderAmount, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                view_count = view_count + VALUES(view_count),
                like_count = like_count + VALUES(like_count),
                order_amount = order_amount + VALUES(order_amount),
                updated_at = NOW()
        """.trimIndent()

        rows.forEach { row ->
            entityManager.createNativeQuery(sql)
                .setParameter("statHour", Timestamp.from(row.statHour))
                .setParameter("productId", row.productId)
                .setParameter("viewCount", row.viewCount)
                .setParameter("likeCount", row.likeCount)
                .setParameter("orderAmount", row.orderAmount)
                .executeUpdate()
        }
    }

    /**
     * 특정 시간 버킷의 모든 집계 데이터를 조회
     *
     * @param statHour 조회할 시간 버킷 (시간 단위로 truncate된 Instant)
     * @return 해당 시간 버킷의 모든 ProductHourlyMetric 목록
     */
    @Transactional(readOnly = true)
    override fun findAllByStatHour(statHour: Instant): List<ProductHourlyMetric> {
        return productHourlyMetricJpaRepository.findAllByStatHour(statHour)
    }

    /**
     * 특정 날짜의 모든 시간별 집계 데이터를 조회
     *
     * @param date 조회할 날짜
     * @return 해당 날짜의 모든 ProductHourlyMetric 목록 (00:00 ~ 23:59)
     */
    @Transactional(readOnly = true)
    override fun findAllByDate(date: LocalDate): List<ProductHourlyMetric> {
        val seoulZone = ZoneId.of("Asia/Seoul")
        val startHour = date.atStartOfDay(seoulZone).toInstant()
        val endHour = date.plusDays(1).atStartOfDay(seoulZone).minusNanos(1).toInstant()
        return productHourlyMetricJpaRepository.findAllByStatHourBetween(startHour, endHour)
    }
}
