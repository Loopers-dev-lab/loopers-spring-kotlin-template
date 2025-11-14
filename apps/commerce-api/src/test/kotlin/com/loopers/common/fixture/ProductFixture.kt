package com.loopers.common.fixture

import com.loopers.domain.product.Product
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

object ProductFixture {

    fun create(
        id: Long = 100L,
        name: String = "테스트상품",
        price: BigDecimal = BigDecimal("10000.00"),
        brandId: Long = 10L,
    ): Product {
        val product = mockk<Product>()
        every { product.id } returns id
        every { product.name } returns name
        every { product.price } returns price
        every { product.brandId } returns brandId
        return product
    }
}
