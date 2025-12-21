package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * 상품별 집계 데이터
 */
@Entity
@Table(name = "product_metrics")
class ProductMetrics(
    @Id
    val productId: Long,

    @Column(nullable = false)
    var likesCount: Int = 0,

    @Column(nullable = false)
    var viewCount: Int = 0,

    @Column(nullable = false)
    var salesCount: Int = 0,

    @Version // 낙관적 락
    var version: Long = 0,

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun incrementLikes() {
        likesCount++
        updatedAt = Instant.now()
    }

    fun decrementLikes() {
        if (likesCount <= 0) return

        likesCount--
        updatedAt = Instant.now()
    }

    fun incrementViews() {
        viewCount++
        updatedAt = Instant.now()
    }

    fun incrementSales(quantity: Int = 1) {
        salesCount += quantity
        updatedAt = Instant.now()
    }


}
