package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "order_detail")
class OrderDetail(

    @Column(name = "brand_name", nullable = false)
    val brandName: String,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    @Column(name = "quantity", nullable = false)
    val quantity: Long,

    @Column(name = "price", nullable = false)
    val price: Long,

    @Column(name = "ref_brand_id", nullable = false)
    val brandId: Long,

    @Column(name = "ref_product_id", nullable = false)
    val productId: Long,

    @Column(name = "ref_order_id", nullable = false)
    val orderId: Long,

    ) : BaseEntity() {

    companion object {
        fun create(
            quantity: Long,
            brand: Brand,
            product: Product,
            order: Order,
        ): OrderDetail {
            require(quantity > 0) { "주문 수량은 0보다 커야 합니다." }
            require(quantity <= 999) { "주문 수량은 999개를 초과할 수 없습니다." }

            return OrderDetail(
                brandName = brand.name,
                productName = product.name,
                quantity = quantity,
                price = product.price,
                brandId = brand.id,
                productId = product.id,
                orderId = order.id,
            )
        }
    }
}
