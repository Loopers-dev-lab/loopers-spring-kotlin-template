package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product

object OrderCommand {

    data class Create(
        val userId: Long,
        val totalAmount: Long,
        val items: List<OrderDetailCommand>,
        val brands: List<Brand>,
        val products: List<Product>,
    )

    data class OrderDetailCommand(
        val productId: Long,
        val quantity: Long,
    )
}
