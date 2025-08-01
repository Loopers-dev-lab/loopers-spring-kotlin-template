package com.loopers.domain.product

import com.loopers.domain.product.entity.Product
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.Test

class ProductTest {

    @Test
    fun `상품을 정상적으로 등록한다`() {
        // when
        val product = Product.create(1L, "상품명", "설명", BigDecimal(15000))

        // then
        assertThat(product.brandId).isEqualTo(1L)
        assertThat(product.name.value).isEqualTo("상품명")
        assertThat(product.description.value).isEqualTo("설명")
        assertThat(product.price.value).isEqualTo(BigDecimal(15000))
    }

    @Test
    fun `상품 이름이 비어있으면 예외가 발생한다`() {
        // when & then
        assertThrows<CoreException> {
            Product.create(1L, "", "설명", BigDecimal(10000))
        }
    }

    @Test
    fun `상품 가격이 0원 이하면 예외가 발생한다`() {
        // when & then
        assertThrows<CoreException> {
            Product.create(1L, "상품명", "설명", BigDecimal(-100))
        }
    }
}
