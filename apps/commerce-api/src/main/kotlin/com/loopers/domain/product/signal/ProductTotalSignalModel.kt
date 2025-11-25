package com.loopers.domain.product.signal

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_total_signals",
    indexes = [
        Index(
            name = "product_total_signal_ref_product_idx",
            columnList = "ref_product_id",
        ),
    ],
)
class ProductTotalSignalModel(
    @Column
    var refProductId: Long,
) : BaseEntity() {

    @Column
    var likeCount: Long = 0

    fun incrementLikeCount() {
        this.likeCount = this.likeCount.plus(1)
    }

    fun decrementLikeCount() {
        this.likeCount = this.likeCount.minus(1).coerceAtLeast(0)
    }
}
