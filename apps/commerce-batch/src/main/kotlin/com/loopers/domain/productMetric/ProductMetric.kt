package com.loopers.domain.productMetric

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_metrics")
class ProductMetric(
    @Column(nullable = false)
    var refProductId: Long,

    @Column(nullable = false)
    var likeCount: Long = 0,

    @Column(nullable = false)
    var viewCount: Long = 0,

    @Column(nullable = false)
    var salesCount: Long = 0,

    @Column(nullable = false)
    var dateTime: String,
) : BaseEntity()

