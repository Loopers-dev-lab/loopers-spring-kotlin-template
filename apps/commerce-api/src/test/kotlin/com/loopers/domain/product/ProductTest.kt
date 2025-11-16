package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductTest {

    @Test
    fun `create 메서드로 Product 객체를 생성할 수 있다`() {
        // given
        val name = "상품"
        val price = 1000L
        val brandId = 1L

        // when
        val product = Product.create(
            name = name,
            price = price,
            brandId = brandId,
        )

        // then
        assertSoftly { softly ->
            softly.assertThat(product.name).isEqualTo(name)
            softly.assertThat(product.price).isEqualTo(price)
            softly.assertThat(product.brandId).isEqualTo(brandId)
        }
    }

    @ParameterizedTest(name = "0 이하의 값({0}) 사용 시도 => 예외 발생")
    @ValueSource(longs = [0L, -1L, -100L, -1000L])
    fun `물건 금액이 0 이하일 때 예외가 발생한다`(invalidPrice: Long) {
        // given
        val name = "상품"
        val brandId = 1L

        // when & then
        assertThatThrownBy {
            Product.create(
                name = name,
                price = invalidPrice,
                brandId = brandId,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("상품 가격은 0보다 커야 합니다.")
    }
}
