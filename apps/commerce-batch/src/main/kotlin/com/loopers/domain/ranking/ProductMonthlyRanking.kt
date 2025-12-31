package com.loopers.domain.ranking

import com.loopers.infrastructure.converter.YearMonthAttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.YearMonth
import java.time.ZonedDateTime

@Entity
@Table(
    name = "mv_product_rank_monthly",
    indexes = [
        Index(name = "idx_month_period", columnList = "month_period"),
        Index(name = "idx_score", columnList = "score"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_monthly_rank", columnNames = ["product_id", "month_period"]),
    ],
)
class ProductMonthlyRanking(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "ranking", nullable = false)
    val ranking: Int = 0,

    @Column(name = "product_id", nullable = false)
    val productId: Long = 0,

    @Column(name = "score", nullable = false)
    val score: Double = 0.0,

    @Convert(converter = YearMonthAttributeConverter::class)
    @Column(name = "month_period", nullable = false)
    val monthPeriod: YearMonth,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now(),
) {
    companion object {
        fun create(
            ranking: Int,
            productId: Long,
            score: Double,
            monthPeriod: YearMonth,
        ): ProductMonthlyRanking {
            return ProductMonthlyRanking(
                ranking = ranking,
                productId = productId,
                score = score,
                monthPeriod = monthPeriod,
            )
        }
    }
}
