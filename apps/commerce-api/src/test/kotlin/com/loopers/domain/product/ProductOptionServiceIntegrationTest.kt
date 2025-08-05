package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductOption
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class ProductOptionServiceIntegrationTest @Autowired constructor(
    private val productOptionService: ProductOptionService,
    private val productOptionRepository: ProductOptionRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상세 조회")
    @Nested
    inner class Get {
        @Test
        fun `id로 상품 옵션을 조회한다`() {
            // given
            val option = productOptionRepository.save(
                ProductOption.create(1L, 100L, "칼라", "XL", "보여줄이름", BigDecimal("1000")),
            )

            // when
            val productOption = productOptionService.get(option.id)

            // then
            assertThat(productOption.id).isEqualTo(option.id)
        }

        @Test
        fun `존재하지 않는 id를 조회하면 예외가 발생한다`() {
            // expect
            assertThrows<CoreException> {
                productOptionService.get(-1L)
            }
        }
    }

    @DisplayName("목록 조회")
    @Nested
    inner class FindAll {
        @Test
        fun `여러 개의 상품 옵션을 조회한다`() {
            // given
            val option1 = productOptionRepository.save(
                ProductOption.create(1L, 1L, "칼라", "XL", "보여줄이름", BigDecimal("1000")),
            )
            val option2 = productOptionRepository.save(
                ProductOption.create(1L, 2L, "칼라", "XL", "보여줄이름", BigDecimal("1000")),
            )

            // when
            val productOptions = productOptionService.findAll(listOf(option1.id, option2.id))

            // then
            assertThat(productOptions).hasSize(2)
        }

        @Test
        fun `상품 ID로 해당 옵션들을 모두 조회한다`() {
            // given
            val productId = 1L
            productOptionRepository.save(
                ProductOption.create(productId, 1L, "칼라", "XL", "보여줄이름", BigDecimal("1000")),
            )
            productOptionRepository.save(
                ProductOption.create(productId, 2L, "칼라", "XL", "보여줄이름", BigDecimal("1000")),
            )

            // when
            val productOptions = productOptionService.findAll(productId)

            // then
            assertThat(productOptions).hasSize(2)
            assertThat(productOptions).allMatch { it.productId == productId }
        }
    }
}
