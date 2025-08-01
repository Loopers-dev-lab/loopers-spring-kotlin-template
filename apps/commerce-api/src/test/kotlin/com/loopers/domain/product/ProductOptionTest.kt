package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductOption
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.Test

class ProductOptionTest {

    @Test
    fun `옵션을 정상 등록한다`() {
        // given
        val productId = 1L
        val skuId = 100L
        val color = "화이트"
        val size = "M"
        val displayName = "뭐시기저시기"
        val additionalPrice = BigDecimal("1000")

        // when
        val option = ProductOption.create(productId, skuId, color, size, displayName, additionalPrice)

        // then
        assertThat(option.productId).isEqualTo(productId)
        assertThat(option.skuId).isEqualTo(skuId)
        assertThat(option.color).isEqualTo(color)
        assertThat(option.size).isEqualTo(size)
        assertThat(option.displayName.value).isEqualTo(displayName)
        assertThat(option.additionalPrice.value).isEqualByComparingTo(additionalPrice)
    }

    @Test
    fun `추가 금액이 음수이면 예외가 발생한다`() {
        // when & then
        assertThrows<CoreException> {
            ProductOption.create(
                1L,
                1L,
                "칼라",
                "사이즈",
                "보여줄이름",
                BigDecimal("-1000"),
            )
        }
    }
}
