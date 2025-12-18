package com.loopers.domain.product

import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class ProductViewTest {

    @DisplayName("ProductView 생성 테스트")
    @Nested
    inner class Of {

        @DisplayName("of 메서드로 ProductView를 생성할 수 있다")
        @Test
        fun `create ProductView using of method`() {
            // given
            val brand = Brand.of("테스트 브랜드")
            val product = Product.create(
                name = "테스트 상품",
                price = Money.krw(10000),
                brand = brand,
            )
            val stock = Stock.create(productId = product.id, quantity = 100)
            val statistic = ProductStatistic.create(productId = product.id)

            // when
            val productView = ProductView.of(
                product = product,
                stock = stock,
                brand = brand,
                statistic = statistic,
            )

            // then
            assertThat(productView.productId).isEqualTo(product.id)
            assertThat(productView.productName).isEqualTo("테스트 상품")
            assertThat(productView.price).isEqualTo(Money.krw(10000))
            assertThat(productView.status).isEqualTo(ProductSaleStatus.ON_SALE)
            assertThat(productView.brandId).isEqualTo(brand.id)
            assertThat(productView.brandName).isEqualTo("테스트 브랜드")
            assertThat(productView.stockQuantity).isEqualTo(100)
            assertThat(productView.likeCount).isEqualTo(0L)
        }

        @DisplayName("likeCount가 있는 경우 올바르게 매핑된다")
        @Test
        fun `map likeCount correctly when statistic has likes`() {
            // given
            val brand = Brand.of("브랜드")
            val product = Product.create(
                name = "상품",
                price = Money.krw(5000),
                brand = brand,
            )
            val stock = Stock.create(productId = product.id, quantity = 50)
            val statistic = ProductStatistic.of(productId = product.id, likeCount = 10L)

            // when
            val productView = ProductView.of(
                product = product,
                stock = stock,
                brand = brand,
                statistic = statistic,
            )

            // then
            assertThat(productView.likeCount).isEqualTo(10L)
        }
    }
}
