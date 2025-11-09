package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.LikeRepository
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
    private val likeRepository: LikeRepository = mockk()
    private val productQueryService = ProductQueryService(productRepository, stockRepository, likeRepository)

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
        every { likeRepository.countByProductIdIn(listOf(100L, 101L)) } returns mapOf(100L to 10L, 101L to 5L)

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
        every { likeRepository.countByProductIdIn(listOf(100L)) } returns mapOf(100L to 3L)

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
        every { likeRepository.countByProductIdIn(listOf(100L, 101L)) } returns mapOf(100L to 5L, 101L to 10L)

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
        every { likeRepository.countByProductIdIn(listOf(100L)) } returns emptyMap()

        // when
        val result = productQueryService.findProducts(null, "latest", pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].likeCount).isEqualTo(0L)
    }

    @Test
    fun `상품 상세 정보를 조회할 수 있다`() {
        // given
        val brand = createTestBrand(1L, "나이키")
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)
        val stock = Stock(productId = 100L, quantity = 50)

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns stock
        every { likeRepository.countByProductId(100L) } returns 10L

        // when
        val result = productQueryService.getProductDetail(100L)

        // then
        assertThat(result.product.id).isEqualTo(100L)
        assertThat(result.product.name).isEqualTo("운동화")
        assertThat(result.stock.quantity).isEqualTo(50)
        assertThat(result.likeCount).isEqualTo(10L)
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
        val brand = createTestBrand(1L, "나이키")
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns null

        // when & then
        assertThatThrownBy {
            productQueryService.getProductDetail(100L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 정보를 찾을 수 없습니다")
    }
}
