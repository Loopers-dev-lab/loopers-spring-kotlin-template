package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductQueryService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductWithLikeCount
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class ProductFacadeTest {
    private val productRepository: ProductRepository = mockk()
    private val stockRepository: StockRepository = mockk()
    private val likeRepository: LikeRepository = mockk()
    private val productQueryService: ProductQueryService = mockk()

    private val productFacade = ProductFacade(
        productRepository,
        stockRepository,
        likeRepository,
        productQueryService,
    )

    private fun createTestProduct(id: Long, name: String, price: BigDecimal, brand: Brand): Product {
        return Product(
            name = name,
            price = Price(price, Currency.KRW),
            brand = brand,
        ).apply {
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
    fun `상품 목록을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(1L, "나이키")
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)
        val pageable = PageRequest.of(0, 20)

        val productsWithLikeCount = PageImpl(
            listOf(ProductWithLikeCount(product, 10L)),
        )

        every {
            productQueryService.findProducts(null, "latest", pageable)
        } returns productsWithLikeCount

        // when
        val result = productFacade.getProducts(null, "latest", pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].id).isEqualTo(100L)
        assertThat(result.content[0].name).isEqualTo("운동화")
        assertThat(result.content[0].likeCount).isEqualTo(10L)
        assertThat(result.content[0].brand.name).isEqualTo("나이키")
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
        val result = productFacade.getProductDetail(100L)

        // then
        assertThat(result.id).isEqualTo(100L)
        assertThat(result.name).isEqualTo("운동화")
        assertThat(result.stockQuantity).isEqualTo(50)
        assertThat(result.likeCount).isEqualTo(10L)
        assertThat(result.brand.name).isEqualTo("나이키")
    }

    @Test
    fun `존재하지 않는 상품 조회 시 예외가 발생한다`() {
        // given
        every { productRepository.findById(999L) } returns null

        // when & then
        assertThatThrownBy {
            productFacade.getProductDetail(999L)
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
            productFacade.getProductDetail(100L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 정보를 찾을 수 없습니다")
    }
}
