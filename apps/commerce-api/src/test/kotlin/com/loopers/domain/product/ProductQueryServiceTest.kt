package com.loopers.domain.product

import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestProduct
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class ProductQueryServiceTest {
    private val productRepository: ProductRepository = mockk()
    private val stockRepository: StockRepository = mockk()
    private val productQueryService = ProductQueryService(productRepository, stockRepository)

    @Test
    fun `상품 목록을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product1 = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        val product2 = createTestProduct(id = 101L, name = "티셔츠", price = BigDecimal("50000"), brand = brand)

        val products = PageImpl(listOf(product1, product2))
        val pageable = PageRequest.of(0, 20)

        every { productRepository.findAll(null, "latest", pageable) } returns products

        // when
        val result = productQueryService.findProducts(null, "latest", pageable)

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].id).isEqualTo(100L)
        assertThat(result.content[1].id).isEqualTo(101L)
    }

    @Test
    fun `브랜드로 필터링하여 상품을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)

        val products = PageImpl(listOf(product))
        val pageable = PageRequest.of(0, 20)
        val brandId = 1L

        every { productRepository.findAll(brandId, "latest", pageable) } returns products

        // when
        val result = productQueryService.findProducts(brandId, "latest", pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].brand.id).isEqualTo(brandId)
    }

    @Test
    fun `가격순으로 정렬하여 상품을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product1 = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("50000"), brand = brand)
        val product2 = createTestProduct(id = 101L, name = "티셔츠", price = BigDecimal("100000"), brand = brand)

        val products = PageImpl(listOf(product1, product2))
        val pageable = PageRequest.of(0, 20)

        every { productRepository.findAll(null, "price_asc", pageable) } returns products

        // when
        val result = productQueryService.findProducts(null, "price_asc", pageable)

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.content[0].price.amount).isEqualTo(BigDecimal("50000"))
        assertThat(result.content[1].price.amount).isEqualTo(BigDecimal("100000"))
    }

    @Test
    fun `상품 상세 정보를 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        val stock = Stock(productId = 100L, quantity = 50)

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns stock

        // when
        val result = productQueryService.getProductDetail(100L)

        // then
        assertThat(result.product.id).isEqualTo(100L)
        assertThat(result.product.name).isEqualTo("운동화")
        assertThat(result.stock.quantity).isEqualTo(50)
    }

    @Test
    fun `여러 ID로 상품을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product1 = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        val product2 = createTestProduct(id = 101L, name = "티셔츠", price = BigDecimal("50000"), brand = brand)

        every { productRepository.findByIdInAndDeletedAtIsNull(listOf(100L, 101L)) } returns listOf(product1, product2)

        // when
        val result = productQueryService.getProductsByIds(listOf(100L, 101L))

        // then
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(100L, 101L)
    }

    @Test
    fun `존재하지 않는 상품 조회 시 예외가 발생한다`() {
        // given
        every { productRepository.findById(999L) } returns null

        // when & then
        assertThatThrownBy {
            productQueryService.getProductDetail(999L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }

    @Test
    fun `재고 정보가 없는 상품 조회 시 예외가 발생한다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns null

        // when & then
        assertThatThrownBy {
            productQueryService.getProductDetail(100L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 정보를 찾을 수 없습니다")
    }
}
