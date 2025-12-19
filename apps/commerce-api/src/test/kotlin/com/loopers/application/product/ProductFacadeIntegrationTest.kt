package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.task.scheduling.enabled=false"])
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @MockBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 ID로 상품을 조회할 수 있다")
    @Test
    fun getProduct() {
        val brand = brandJpaRepository.save(Brand("테스트브랜드", "설명"))
        val product = productJpaRepository.save(
            Product("상품1", "설명", Money.of(10000L), Stock.of(100), 1L)
        )

        val result = productFacade.getProduct(product.id)

        assertThat(result).isNotNull
        assertThat(result.name).isEqualTo("상품1")
        assertThat(result.price.amount).isEqualTo(10000L)
    }

    @DisplayName("존재하지 않는 상품 ID로 조회 시 예외가 발생한다")
    @Test
    fun failToGetProductWhenNotExists() {
        val exception = assertThrows<CoreException> {
            productFacade.getProduct(999L)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.PRODUCT_NOT_FOUND)
    }

    @DisplayName("상품 목록을 페이징하여 조회할 수 있다")
    @Test
    fun getProducts() {
        val brand = brandJpaRepository.save(Brand("테스트브랜드", "설명"))
        productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), brand.id!!))
        productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), brand.id!!))
        productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), brand.id!!))

        val pageable = PageRequest.of(0, 10)
        val result = productFacade.getProducts(null, ProductSortType.LATEST, pageable)

        assertThat(result.content).hasSize(3)
        assertThat(result.totalElements).isEqualTo(3)
    }

    @DisplayName("브랜드 ID로 필터링하여 상품을 조회할 수 있다")
    @Test
    fun getProductsByBrandId() {
        val brand1 = brandJpaRepository.save(Brand("브랜드1", "설명1"))
        val brand2 = brandJpaRepository.save(Brand("브랜드2", "설명2"))

        productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), brand1.id!!))
        productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), brand1.id!!))
        productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), brand2.id!!))

        val pageable = PageRequest.of(0, 10)
        val result = productFacade.getProducts(brand1.id, ProductSortType.LATEST, pageable)

        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(2)
    }

    @DisplayName("상품 목록 조회 시 페이지 크기에 맞게 조회된다")
    @Test
    fun getProductsWithPageSize() {
        val brand = brandJpaRepository.save(Brand("테스트브랜드", "설명"))
        productJpaRepository.save(Product("상품1", "설명1", Money.of(10000L), Stock.of(100), 1L))
        productJpaRepository.save(Product("상품2", "설명2", Money.of(20000L), Stock.of(50), 1L))
        productJpaRepository.save(Product("상품3", "설명3", Money.of(15000L), Stock.of(30), 1L))

        val pageable = PageRequest.of(0, 2)
        val result = productFacade.getProducts(null, ProductSortType.LATEST, pageable)

        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(2)
    }
}
