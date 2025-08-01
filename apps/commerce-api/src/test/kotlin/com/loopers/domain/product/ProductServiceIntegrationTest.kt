package com.loopers.domain.product

import com.loopers.domain.product.dto.command.ProductCommand
import com.loopers.domain.product.entity.Product
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
class ProductServiceIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 등록")
    @Nested
    inner class Register {

        @Test
        fun `상품을 정상적으로 등록한다`() {
            // given
            val command = ProductCommand.RegisterProduct(1L, "구찌팔찌", "비싸다", BigDecimal(1000))

            // when
            val saved = productService.register(command)

            // then
            val product = productService.get(saved.id)
            assertThat(product.id).isEqualTo(saved.id)
        }
    }

    @DisplayName("상세 조회")
    @Nested
    inner class Get {

        @Test
        fun `상품을 ID로 조회할 수 있다`() {
            // given
            val saved = productRepository.save(
                Product.create(1L, "상품명", "설명", BigDecimal(5000)),
            )

            // when
            val product = productService.get(saved.id)

            // then
            assertThat(product.id).isEqualTo(saved.id)
        }

        @Test
        fun `존재하지 않는 상품 ID로 조회하면 예외가 발생한다`() {
            // expect
            assertThrows<CoreException> {
                productService.get(-1L)
            }
        }
    }

    @DisplayName("목록 조회")
    @Nested
    inner class FindAll {

        @Test
        fun `상품 ID 목록으로 상품 목록을 조회할 수 있다`() {
            // given
            val product1 = productRepository.save(Product.create(1L, "상품1", "설명1", BigDecimal(1000)))
            val product2 = productRepository.save(Product.create(1L, "상품2", "설명2", BigDecimal(2000)))

            // when
            val product = productService.findAll(listOf(product1.id, product2.id))

            // then
            assertThat(product).hasSize(2)
            assertThat(product.map { it.id }).containsExactlyInAnyOrder(product1.id, product2.id)
        }
    }
}
