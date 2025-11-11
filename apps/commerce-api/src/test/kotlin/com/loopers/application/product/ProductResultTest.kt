package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.ProductLike
import com.loopers.domain.product.Product
import com.loopers.support.util.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProductResultTest {

    @Test
    fun `상품, 좋아요, 브랜드 정보를 조합하여 ProductInfo를 생성한다`() {
        // given
        val brand = Brand.create("테스트 브랜드").withId(1L)
        val product = Product.create("테스트 상품", 10000, brand.id).withId(1)
        val productLikes = listOf(
            ProductLike.create(product.id, 1L),
            ProductLike.create(product.id, 2L),
        )

        // when
        val result = ProductResult.Info.from(product, productLikes, listOf(brand))

        // then
        assertSoftly { softly ->
            softly.assertThat(result.name).isEqualTo("테스트 상품")
            softly.assertThat(result.price).isEqualTo(10000)
            softly.assertThat(result.brandName).isEqualTo("테스트 브랜드")
            softly.assertThat(result.likeCount).isEqualTo(2)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "0, 0",
        "1, 1",
        "5, 5",
    )
    fun `좋아요 개수를 정확히 계산한다`(likeCount: Int, expectedCount: Long) {
        // given
        val brand = Brand.create("테스트 브랜드").withId(1L)
        val product = Product.create("테스트 상품", 10000, brand.id).withId(1)
        val productLikes = (1..likeCount).map { ProductLike.create(product.id, it.toLong()) }

        // when
        val result = ProductResult.Info.from(product, productLikes, listOf(brand))

        // then
        assertThat(result.likeCount).isEqualTo(expectedCount)
    }

    @Test
    fun `좋아요가 없을 때 likeCount는 0이다`() {
        // given
        val brand = Brand.create("테스트 브랜드").withId(1)
        val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
        val productLikes = emptyList<ProductLike>()

        // when
        val result = ProductResult.Info.from(product, productLikes, listOf(brand))

        // then
        assertThat(result.likeCount).isZero()
    }

    @Test
    fun `다른 상품의 좋아요는 카운트에 포함하지 않는다`() {
        // given
        val brand = Brand.create("테스트 브랜드").withId(1)
        val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
        val productLikes = listOf(
            ProductLike.create(product.id, 1L),
            ProductLike.create(999L, 2L),
            ProductLike.create(product.id, 3L),
        )

        // when
        val result = ProductResult.Info.from(product, productLikes, listOf(brand))

        // then
        assertThat(result.likeCount).isEqualTo(2)
    }
}
