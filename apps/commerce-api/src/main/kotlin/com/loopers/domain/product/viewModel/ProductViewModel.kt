package com.loopers.domain.product.viewModel

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(
    name = "product_views",
    indexes = [
        Index(name = "product_view_ref_product_idx", columnList = "ref_product_id"),
        Index(name = "product_view_ref_brand_idx", columnList = "ref_brand_id"),
    ],
)
class ProductViewModel(
    @Column
    val refProductId: Long,

    @Column
    var productName: String,

    @Column
    var price: Money,

    @Column
    var refBrandId: Long,

    @Column
    var brandName: String,

    @Column
    var stockAmount: Long,

    @Column
    var likeCount: Long,

    ) : BaseEntity() {

    @Version
    val version: Long = 0L

    fun updateBrandName(brandName: String) {
        this.brandName = brandName
    }

    fun updateStockAmount(stockAmount: Long) {
        this.stockAmount = stockAmount
    }

    fun updateLikeCount(likeCount: Long) {
        this.likeCount = likeCount
    }
}
