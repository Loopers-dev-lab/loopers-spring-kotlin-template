package com.loopers.domain.product

import com.loopers.IntegrationTestSupport
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.domain.product.signal.ProductTotalSignalRepository
import com.loopers.domain.product.stock.StockModel
import com.loopers.domain.product.stock.StockRepository
import com.loopers.support.cache.CacheKeys
import com.loopers.support.cache.CacheTemplate
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.test.Test

class ProductDetailServiceTest(
    private val productDetailService: ProductDetailService,
    private val databaseCleanUp: DatabaseCleanUp,
    @MockitoSpyBean
    private val productRepository: ProductRepository,
    @MockitoSpyBean
    private val brandRepository: BrandRepository,
    @MockitoSpyBean
    private val productTotalSignalRepository: ProductTotalSignalRepository,
    @MockitoSpyBean
    private val stockRepository: StockRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val cacheTemplate: CacheTemplate,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisTemplate.keys("*").forEach { redisTemplate.delete(it) }
        clearInvocations(productRepository, brandRepository, productTotalSignalRepository, stockRepository)
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
            price = Money(BigDecimal.valueOf(10000)),
            refBrandId = brand.id,
        )
        productRepository.save(product)

        val stock = StockModel.create(product.id, 100)
        stockRepository.save(stock)

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
            price = Money(BigDecimal.valueOf(5000)),
            refBrandId = 1,
        )
        productRepository.save(product)

        val stock = StockModel.create(product.id, 100)
        stockRepository.save(stock)

        val exception = assertThrows<CoreException> {
            productDetailService.getProductDetailBy(1)
        }
        Assertions.assertThat(exception.message).isEqualTo("해당 브랜드는 존재하지 않습니다.")
    }

    @DisplayName("캐시에 데이터가 있으면 Repository를 호출하지 않는다")
    @Test
    fun getProductDetailWithCache_shouldNotCallRepository() {
        val productId = 999L
        val cacheKey = CacheKeys.ProductDetail(productId)

        val cachedData = ProductDetailResult(
            id = productId,
            name = "CachedProduct",
            stock = 100,
            likeCount = 50,
            brandId = 1L,
            brandName = "CachedBrand",
        )

        cacheTemplate.put(cacheKey, cachedData)

        assertThat(redisTemplate.hasKey(cacheKey.key)).isTrue

        clearInvocations(productRepository, brandRepository, productTotalSignalRepository, stockRepository)

        val result = productDetailService.getProductDetailBy(productId)

        assertThat(result.id).isEqualTo(productId)
        assertThat(result.name).isEqualTo("CachedProduct")
        assertThat(result.stock).isEqualTo(100)
        assertThat(result.likeCount).isEqualTo(50)
        assertThat(result.brandName).isEqualTo("CachedBrand")

        verify(productRepository, times(0)).findById(productId)
        verify(brandRepository, times(0)).findById(org.mockito.kotlin.any())
        verify(productTotalSignalRepository, times(0)).findByProductId(productId)
        verify(stockRepository, times(0)).findByRefProductId(productId)
    }
}
