package com.loopers.domain.product

import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class ProductTest {
    @DisplayName("상품 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("새 상품이 생성된다")
        @Test
        fun `create new product`() {
            // given
            val brand = createBrand()
            val name = "맥북 프로"
            val price = Money.krw(2000000)

            // when
            val product = createNewProduct(
                brand = brand,
                name = name,
                price = price,
            )

            // then
            assertThat(product.brandId).isEqualTo(brand.id)
            assertThat(product.name).isEqualTo(name)
            assertThat(product.price).isEqualTo(price)
        }

        @DisplayName("of 팩토리 메서드로 상품을 생성할 수 있다")
        @Test
        fun `create product with of factory method`() {
            // given
            val brandId = 1L
            val name = "테스트 상품"
            val price = Money.krw(10000)

            // when
            val product = Product.of(
                brandId = brandId,
                name = name,
                price = price,
            )

            // then
            assertThat(product.brandId).isEqualTo(brandId)
            assertThat(product.name).isEqualTo(name)
            assertThat(product.price).isEqualTo(price)
        }
    }

    private fun createNewProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        brand: Brand = createBrand(),
    ): Product {
        return Product.create(
            name = name,
            price = price,
            brand = brand,
        )
    }

    private fun createBrand(
        name: String = "테스트 브랜드",
    ): Brand {
        return Brand.of(name)
    }
}
