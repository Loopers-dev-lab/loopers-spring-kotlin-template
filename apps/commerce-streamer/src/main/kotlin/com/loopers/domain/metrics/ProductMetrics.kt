package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 상품 메트릭 집계 테이블 (일자별 이력 관리)
 *
 * Kafka 이벤트를 통해 수집된 상품 관련 메트릭을 일자별로 집계
 * - 좋아요 수
 * - 조회 수
 * - 판매 수량
 */
@Entity
@Table(
    name = "product_metrics",
    indexes = [
        Index(name = "idx_metric_date", columnList = "metric_date"),
        Index(name = "idx_product_id", columnList = "product_id"),
    ],
)
class ProductMetrics(
    @EmbeddedId
    val id: ProductMetricsId,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,

    @Column(name = "sold_count", nullable = false)
    var soldCount: Long = 0,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now(),
) {
    fun increaseLikeCount() {
        this.likeCount++
        this.updatedAt = ZonedDateTime.now()
    }

    fun decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--
        }
        this.updatedAt = ZonedDateTime.now()
    }

    fun increaseViewCount() {
        this.viewCount++
        this.updatedAt = ZonedDateTime.now()
    }

    fun increaseSoldCount(quantity: Int) {
        this.soldCount += quantity
        this.updatedAt = ZonedDateTime.now()
    }

    fun decreaseSoldCount(quantity: Int) {
        if (this.soldCount >= quantity) {
            this.soldCount -= quantity
        }
        this.updatedAt = ZonedDateTime.now()
    }

    companion object {
        fun create(productId: Long, metricDate: LocalDate): ProductMetrics {
            return ProductMetrics(
                id = ProductMetricsId(productId, metricDate),
                likeCount = 0,
                viewCount = 0,
                soldCount = 0,
            )
        }
    }
}
