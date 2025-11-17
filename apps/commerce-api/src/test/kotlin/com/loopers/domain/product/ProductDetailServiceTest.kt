package com.loopers.domain.product

import com.loopers.IntegrationTestSupport
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.infrastructure.product.signal.ProductTotalSignalJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.test.Test

class ProductDetailServiceTest(
    private val productDetailService: ProductDetailService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productTotalSignalRepository: ProductTotalSignalJpaRepository,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 ID로 상품 상세 정보를 조회한다")
    @Test
    @Transactional
    fun getProductDetailSuccess() {
        val brand = BrandModel(
            "Nike",
        )
        brandRepository.save(brand)

        val product = ProductModel.create(
            name = "Lebron23",
            stock = 100,
            price = Money(BigDecimal.valueOf(10000)),
            refBrandId = brand.id,
        )
        productRepository.save(product)

        val productTotalSignal = ProductTotalSignalModel(refProductId = product.id)
        productTotalSignalRepository.save(productTotalSignal)
        productTotalSignal.incrementLikeCount()

        val productDetail = productDetailService.getProductDetailBy(product.id)

        Assertions.assertThat(productDetail.id).isEqualTo(1)
        Assertions.assertThat(productDetail.name).isEqualTo("Lebron23")
        Assertions.assertThat(productDetail.stock).isEqualTo(100)
        Assertions.assertThat(productDetail.likeCount).isEqualTo(1)
    }

    @DisplayName("존재하지 않는 상품 조회 시 예외가 발생한다")
    @Test
    fun getProductDetailFails_whenProductIsNotExists() {
        val exception = assertThrows<CoreException> {
            productDetailService.getProductDetailBy(1)
        }

        Assertions.assertThat(exception.message).isEqualTo("해당 상품은 존재하지 않습니다.")
    }

    @DisplayName("연관된 브랜드가 존재하지 않으면 예외가 발생한다")
    @Test
    fun getProductDetailFails_whenBrandIsNotExists() {
        val product = ProductModel.create(
            name = "Lebron23",
            stock = 100,
            price = Money(BigDecimal.valueOf(5000)),
            refBrandId = 1,
        )
        productRepository.save(product)

        val exception = assertThrows<CoreException> {
            productDetailService.getProductDetailBy(1)
        }
        Assertions.assertThat(exception.message).isEqualTo("해당 브랜드는 존재하지 않습니다.")
    }
}
