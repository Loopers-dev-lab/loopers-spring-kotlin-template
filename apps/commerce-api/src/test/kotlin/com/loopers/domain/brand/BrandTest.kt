package com.loopers.domain.brand

import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class BrandTest {

    @DisplayName("브랜드를 생성할 수 있다")
    @Test
    fun createBrand() {
        val brand = Brand("브랜드1", "브랜드 설명")

        assertAll (
            { assertThat(brand.name).isEqualTo("브랜드1") },
            { assertThat(brand.description).isEqualTo("브랜드 설명") },
            { assertThat(brand.products).isEmpty() }
        )
    }

    @DisplayName("브랜드에 상품을 추가할 수 있다")
    @Test
    fun addProductToBrand() {
        val brand = Brand("브랜드1", "브랜드 설명")
        val product = Product("상품1", "상품 설명", Money.of(1000L), Stock.of(100), brand)

        brand.addProduct(product)

        assertThat(brand.products).hasSize(1)
        assertThat(brand.products[0]).isEqualTo(product)
    }

    @DisplayName("브랜드에 여러 상품을 추가할 수 있따")
    @Test
    fun addMultipleProductToBrand() {
        val brand = Brand("브랜드1", "브랜드 설명")
        val product1 = Product("상품1", "상품 설명1", Money.of(10000L), Stock.of(100), brand)
        val product2 = Product("상품2", "상품 설명2", Money.of(20000L), Stock.of(50), brand)

        brand.addProduct(product1)
        brand.addProduct(product2)

        assertThat(brand.products).hasSize(2)
    }

}
