package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.LikeRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class ProductQueryServiceTest {
    private val productRepository: ProductRepository = mockk()
    private val likeRepository: LikeRepository = mockk()
    private val productQueryService = ProductQueryService(productRepository, likeRepository)

    private fun createTestProduct(id: Long, name: String, price: BigDecimal, brand: Brand): Product {
        return Product(
            name = name,
            price = Price(price, Currency.KRW),
            brand = brand,
        ).apply {
            // Reflection으로 id 설정
            val idField = Product::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
    }

    private fun createTestBrand(id: Long, name: String): Brand {
        return Brand(name = name, description = "Test Description").apply {
            val idField = Brand::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
    }

    @Test
    fun `상품 목록 조회 시 각 상품의 좋아요 수를 함께 반환한다`() {
        // given
        val brand = createTestBrand(1L, "나이키")
        val product1 = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)
        val product2 = createTestProduct(101L, "티셔츠", BigDecimal("50000"), brand)

        val products = PageImpl(listOf(product1, product2))
        val pageable = PageRequest.of(0, 20)

        every { productRepository.findAll(null, "latest", pageable) } returns products
        every { likeRepository.countByProductId(100L) } returns 10L
        every { likeRepository.countByProductId(101L) } returns 5L

        // when
        val result = productQueryService.findProducts(null, "latest", pageable)

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].product.id).isEqualTo(100L)
        assertThat(result.content[0].likeCount).isEqualTo(10L)
        assertThat(result.content[1].product.id).isEqualTo(101L)
        assertThat(result.content[1].likeCount).isEqualTo(5L)
    }

    @Test
    fun `브랜드로 필터링하여 상품을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(1L, "나이키")
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)

        val products = PageImpl(listOf(product))
        val pageable = PageRequest.of(0, 20)
        val brandId = 1L

        every { productRepository.findAll(brandId, "latest", pageable) } returns products
        every { likeRepository.countByProductId(100L) } returns 3L

        // when
        val result = productQueryService.findProducts(brandId, "latest", pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].product.brand.id).isEqualTo(brandId)
        assertThat(result.content[0].likeCount).isEqualTo(3L)
    }

    @Test
    fun `가격순으로 정렬하여 상품을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(1L, "나이키")
        val product1 = createTestProduct(100L, "운동화", BigDecimal("50000"), brand)
        val product2 = createTestProduct(101L, "티셔츠", BigDecimal("100000"), brand)

        val products = PageImpl(listOf(product1, product2))
        val pageable = PageRequest.of(0, 20)

        every { productRepository.findAll(null, "price", pageable) } returns products
        every { likeRepository.countByProductId(100L) } returns 5L
        every { likeRepository.countByProductId(101L) } returns 10L

        // when
        val result = productQueryService.findProducts(null, "price", pageable)

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].product.price.amount).isEqualTo(BigDecimal("50000"))
        assertThat(result.content[1].product.price.amount).isEqualTo(BigDecimal("100000"))
    }

    @Test
    fun `좋아요 수가 0인 상품도 조회할 수 있다`() {
        // given
        val brand = createTestBrand(1L, "나이키")
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)

        val products = PageImpl(listOf(product))
        val pageable = PageRequest.of(0, 20)

        every { productRepository.findAll(null, "latest", pageable) } returns products
        every { likeRepository.countByProductId(100L) } returns 0L

        // when
        val result = productQueryService.findProducts(null, "latest", pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].likeCount).isEqualTo(0L)
    }
}
