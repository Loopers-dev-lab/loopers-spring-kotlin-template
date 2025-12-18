package com.loopers.application.product

import com.loopers.domain.product.ProductSaleStatus
import com.loopers.domain.product.ProductView
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CachedProductDetailV1Test {

    @DisplayName("from 메서드 테스트")
    @Nested
    inner class From {

        @Test
        @DisplayName("ProductView로부터 CachedProductDetailV1을 올바르게 생성한다")
        fun `create CachedProductDetailV1 from ProductView correctly`() {
            // given
            val productView = ProductView(
                productId = 1L,
                productName = "테스트 상품",
                price = Money.krw(10000),
                status = ProductSaleStatus.ON_SALE,
                brandId = 100L,
                brandName = "테스트 브랜드",
                stockQuantity = 50,
                likeCount = 25L,
            )

            // when
            val cached = CachedProductDetailV1.from(productView)

            // then
            assertThat(cached.productId).isEqualTo(1L)
            assertThat(cached.productName).isEqualTo("테스트 상품")
            assertThat(cached.price).isEqualTo(10000L)
            assertThat(cached.status).isEqualTo("ON_SALE")
            assertThat(cached.brandId).isEqualTo(100L)
            assertThat(cached.brandName).isEqualTo("테스트 브랜드")
            assertThat(cached.stockQuantity).isEqualTo(50)
            assertThat(cached.likeCount).isEqualTo(25L)
        }

        @Test
        @DisplayName("SOLD_OUT 상태의 ProductView를 올바르게 변환한다")
        fun `convert SOLD_OUT status ProductView correctly`() {
            // given
            val productView = ProductView(
                productId = 2L,
                productName = "품절 상품",
                price = Money.krw(5000),
                status = ProductSaleStatus.SOLD_OUT,
                brandId = 200L,
                brandName = "브랜드",
                stockQuantity = 0,
                likeCount = 100L,
            )

            // when
            val cached = CachedProductDetailV1.from(productView)

            // then
            assertThat(cached.status).isEqualTo("SOLD_OUT")
            assertThat(cached.stockQuantity).isEqualTo(0)
        }
    }

    @DisplayName("toProductView 메서드 테스트")
    @Nested
    inner class ToProductView {

        @Test
        @DisplayName("CachedProductDetailV1로부터 ProductView를 올바르게 생성한다")
        fun `create ProductView from CachedProductDetailV1 correctly`() {
            // given
            val cached = CachedProductDetailV1(
                productId = 1L,
                productName = "테스트 상품",
                price = 10000L,
                status = "ON_SALE",
                brandId = 100L,
                brandName = "테스트 브랜드",
                stockQuantity = 50,
                likeCount = 25L,
            )

            // when
            val productView = cached.toProductView()

            // then
            assertThat(productView.productId).isEqualTo(1L)
            assertThat(productView.productName).isEqualTo("테스트 상품")
            assertThat(productView.price).isEqualTo(Money.krw(10000))
            assertThat(productView.status).isEqualTo(ProductSaleStatus.ON_SALE)
            assertThat(productView.brandId).isEqualTo(100L)
            assertThat(productView.brandName).isEqualTo("테스트 브랜드")
            assertThat(productView.stockQuantity).isEqualTo(50)
            assertThat(productView.likeCount).isEqualTo(25L)
        }

        @Test
        @DisplayName("SOLD_OUT 상태를 올바르게 변환한다")
        fun `convert SOLD_OUT status correctly`() {
            // given
            val cached = CachedProductDetailV1(
                productId = 2L,
                productName = "품절 상품",
                price = 5000L,
                status = "SOLD_OUT",
                brandId = 200L,
                brandName = "브랜드",
                stockQuantity = 0,
                likeCount = 100L,
            )

            // when
            val productView = cached.toProductView()

            // then
            assertThat(productView.status).isEqualTo(ProductSaleStatus.SOLD_OUT)
        }
    }

    @DisplayName("from과 toProductView 변환 테스트")
    @Nested
    inner class RoundTrip {

        @Test
        @DisplayName("from과 toProductView가 동일한 데이터를 유지한다")
        fun `from and toProductView maintain same data`() {
            // given
            val originalView = ProductView(
                productId = 1L,
                productName = "테스트 상품",
                price = Money.krw(10000),
                status = ProductSaleStatus.ON_SALE,
                brandId = 100L,
                brandName = "테스트 브랜드",
                stockQuantity = 50,
                likeCount = 25L,
            )

            // when
            val cached = CachedProductDetailV1.from(originalView)
            val convertedView = cached.toProductView()

            // then
            assertThat(convertedView.productId).isEqualTo(originalView.productId)
            assertThat(convertedView.productName).isEqualTo(originalView.productName)
            assertThat(convertedView.price).isEqualTo(originalView.price)
            assertThat(convertedView.status).isEqualTo(originalView.status)
            assertThat(convertedView.brandId).isEqualTo(originalView.brandId)
            assertThat(convertedView.brandName).isEqualTo(originalView.brandName)
            assertThat(convertedView.stockQuantity).isEqualTo(originalView.stockQuantity)
            assertThat(convertedView.likeCount).isEqualTo(originalView.likeCount)
        }
    }
}
