package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "mv_product_rank_weekly")
class ProductRankWeekly(
    val productId: Long,
    val yearWeek: String,
    val score: Double,
    val rankPosition: Long
) : BaseEntity()
