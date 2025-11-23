package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.ProductLikeCount
import com.loopers.domain.product.Product
import com.loopers.support.fixtures.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProductResultTest {

    @Nested
    @DisplayName("ListInfo from 메서드")
    inner class ListInfoFrom {

        @Test
        fun `상품, 좋아요 수, 브랜드 정보를 조합하여 ListInfo를 생성한다`() {
            // given
            val brand = Brand.create("테스트 브랜드").withId(1L)
            val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
            val productLikeCounts = listOf(
                ProductLikeCount.create(product.id, 5L)
            )

            // when
            val result = ProductResult.ListInfo.from(product, productLikeCounts, listOf(brand))

            // then
            assertSoftly { softly ->
                softly.assertThat(result.name).isEqualTo("테스트 상품")
                softly.assertThat(result.price).isEqualTo(10000)
                softly.assertThat(result.brandName).isEqualTo("테스트 브랜드")
                softly.assertThat(result.likeCount).isEqualTo(5L)
            }
        }

        @ParameterizedTest
        @CsvSource(
            "0",
            "1",
            "5",
            "100",
        )
        fun `좋아요 개수를 정확히 반영한다`(likeCount: Long) {
            // given
            val brand = Brand.create("테스트 브랜드").withId(1L)
            val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
            val productLikeCounts = listOf(
                ProductLikeCount.create(product.id, likeCount)
            )

            // when
            val result = ProductResult.ListInfo.from(product, productLikeCounts, listOf(brand))

            // then
            assertThat(result.likeCount).isEqualTo(likeCount)
        }
    }

    @Nested
    @DisplayName("DetailInfo from 메서드")
    inner class DetailInfoFrom {

        @Test
        fun `상품, 좋아요 여부, 좋아요 수, 브랜드 정보를 조합하여 DetailInfo를 생성한다`() {
            // given
            val brand = Brand.create("테스트 브랜드").withId(1L)
            val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
            val userLiked = true
            val likeCount = 10L

            // when
            val result = ProductResult.DetailInfo.from(product, userLiked, likeCount, brand)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.id).isEqualTo(product.id)
                softly.assertThat(result.name).isEqualTo("테스트 상품")
                softly.assertThat(result.price).isEqualTo(10000)
                softly.assertThat(result.brandName).isEqualTo("테스트 브랜드")
                softly.assertThat(result.likeCount).isEqualTo(10L)
                softly.assertThat(result.likedByMe).isTrue()
            }
        }

        @Test
        fun `사용자가 좋아요를 누르지 않았으면 likedByMe는 false다`() {
            // given
            val brand = Brand.create("테스트 브랜드").withId(1L)
            val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
            val userLiked = false
            val likeCount = 5L

            // when
            val result = ProductResult.DetailInfo.from(product, userLiked, likeCount, brand)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.likeCount).isEqualTo(5L)
                softly.assertThat(result.likedByMe).isFalse()
            }
        }

        @Test
        fun `좋아요가 없을 때 likeCount는 0이고 likedByMe는 false다`() {
            // given
            val brand = Brand.create("테스트 브랜드").withId(1L)
            val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)
            val userLiked = false
            val likeCount = 0L

            // when
            val result = ProductResult.DetailInfo.from(product, userLiked, likeCount, brand)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.likeCount).isZero()
                softly.assertThat(result.likedByMe).isFalse()
            }
        }

        @ParameterizedTest
        @CsvSource(
            "true, 1",
            "true, 10",
            "false, 0",
            "false, 5",
        )
        fun `좋아요 여부와 개수를 정확히 반영한다`(userLiked: Boolean, likeCount: Long) {
            // given
            val brand = Brand.create("테스트 브랜드").withId(1L)
            val product = Product.create("테스트 상품", 10000, brand.id).withId(1L)

            // when
            val result = ProductResult.DetailInfo.from(product, userLiked, likeCount, brand)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.likeCount).isEqualTo(likeCount)
                softly.assertThat(result.likedByMe).isEqualTo(userLiked)
            }
        }
    }
}
