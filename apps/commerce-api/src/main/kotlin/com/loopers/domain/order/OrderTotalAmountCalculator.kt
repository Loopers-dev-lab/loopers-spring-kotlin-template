package com.loopers.domain.order

import com.loopers.domain.product.Product

object OrderTotalAmountCalculator {

    fun calculate(items: List<OrderCommand.OrderDetailCommand>, products: List<Product>): Long {
        val productPriceMap = products.associateBy({ it.id }, { it.price })

        return items.sumOf { item ->
            val price = productPriceMap[item.productId]
                ?: throw IllegalArgumentException("상품 ID ${item.productId}에 해당하는 상품을 찾을 수 없습니다.")
            price * item.quantity
        }
    }
}
