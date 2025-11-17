package com.loopers.support.fixtures

import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import java.time.ZonedDateTime

object ProductFixtures {

    fun createProduct(
        name: String = "name",
        price: Long = 1000,
        brandId: Long = 1L,
    ): Product {
        return Product.create(
            name = name,
            price = price,
            brandId = brandId,
        ).withCreatedAt(ZonedDateTime.now())
    }

    fun createProduct(
        id: Long = 1L,
        name: String = "name",
        price: Long = 1000,
        brandId: Long = 1L,
    ): Product {
        return Product.create(
            name = name,
            price = price,
            brandId = brandId,
        ).withId(id).withCreatedAt(ZonedDateTime.now())
    }

    fun createStock(
        productId: Long,
        quantity: Long,
    ): Stock {
        return Stock.create(
            productId = productId,
            quantity = quantity,
        )
    }

    fun createStockWithId(
        id: Long = 1L,
        productId: Long,
        quantity: Long,
    ): Stock {
        return Stock.create(
            productId = productId,
            quantity = quantity,
        ).withId(id)
    }
}
