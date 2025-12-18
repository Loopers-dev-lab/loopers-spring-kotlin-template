package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class ProductServiceIntegrationTest @Autowired constructor(
    private val productService: ProductService,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 조회 통합테스트")
    @Nested
    inner class FindProductById {

        @DisplayName("존재하는 상품을 조회하면 상품이 반환된다")
        @Test
        fun `return product when product exists`() {
            // given
            val product = createProduct()

            // when
            val foundProduct = productService.findProductById(product.id)

            // then
            assertThat(foundProduct.id).isEqualTo(product.id)
            assertThat(foundProduct.name).isEqualTo(product.name)
        }

        @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
        @Test
        fun `throw exception when product not exists`() {
            // given
            val notExistId = 999L

            // when
            val exception = assertThrows<CoreException> {
                productService.findProductById(notExistId)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("상품을 찾을 수 없습니다")
        }
    }

    @DisplayName("상품 상세 조회 통합테스트")
    @Nested
    inner class FindProductViewById {

        @DisplayName("존재하는 상품을 조회하면 상품, 재고, 브랜드, 통계 정보가 함께 반환된다")
        @Test
        fun `return product view with all related data when product exists`() {
            // given
            val product = createProduct(stockQuantity = 100)

            // when
            val productView = productService.findProductViewById(product.id)

            // then
            assertAll(
                { assertThat(productView.productId).isEqualTo(product.id) },
                { assertThat(productView.stockQuantity).isEqualTo(100) },
                { assertThat(productView.brandId).isEqualTo(product.brandId) },
                { assertThat(productView.likeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
        @Test
        fun `throw exception when product not exists`() {
            // given
            val notExistId = 999L

            // when
            val exception = assertThrows<CoreException> {
                productService.findProductViewById(notExistId)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("상품을 찾을 수 없습니다")
        }
    }

    @DisplayName("상품 검색 통합테스트")
    @Nested
    inner class SearchProducts {

        @DisplayName("페이징 조건으로 상품을 검색할 수 있다")
        @Test
        fun `return product views with paging`() {
            // given
            createProduct(name = "상품1")
            createProduct(name = "상품2")
            createProduct(name = "상품3")

            // when
            val command = ProductCommand.FindProducts(
                page = 0,
                size = 2,
            )
            val result = productService.findProducts(command)

            // then
            assertThat(result.content).hasSize(2)
            assertThat(result.hasNext()).isTrue()
        }

        @DisplayName("브랜드로 필터링하여 상품을 검색할 수 있다")
        @Test
        fun `return product views filtered by brand`() {
            // given
            val brand1 = brandRepository.save(Brand.create("브랜드1"))
            val brand2 = brandRepository.save(Brand.create("브랜드2"))

            createProduct(name = "상품1", brandId = brand1.id)
            createProduct(name = "상품2", brandId = brand1.id)
            createProduct(name = "상품3", brandId = brand2.id)

            // when
            val command = ProductCommand.FindProducts(
                brandId = brand1.id,
            )
            val result = productService.findProducts(command)

            // then
            assertThat(result.content).hasSize(2)
            assertThat(result.content).allMatch { it.brandId == brand1.id }
        }
    }

    @DisplayName("재고 감소 통합테스트")
    @Nested
    inner class DecreaseStocks {

        @DisplayName("여러 상품의 재고를 한 번에 감소시킬 수 있다")
        @Test
        fun `decrease stocks for multiple products`() {
            // given
            val product1 = createProduct(name = "상품1", stockQuantity = 100)
            val product2 = createProduct(name = "상품2", stockQuantity = 50)

            // when
            val command = ProductCommand.DecreaseStocks(
                units = listOf(
                    ProductCommand.DecreaseStockUnit(product1.id, 10),
                    ProductCommand.DecreaseStockUnit(product2.id, 5),
                ),
            )
            productService.decreaseStocks(command)

            // then
            val updatedStock1 = stockRepository.findByProductId(product1.id)!!
            val updatedStock2 = stockRepository.findByProductId(product2.id)!!
            val updatedProduct1 = productRepository.findById(product1.id)!!
            val updatedProduct2 = productRepository.findById(product2.id)!!
            assertAll(
                { assertThat(updatedStock1.quantity).isEqualTo(90) },
                { assertThat(updatedStock2.quantity).isEqualTo(45) },
                { assertThat(updatedProduct1.status).isEqualTo(ProductSaleStatus.ON_SALE) },
                { assertThat(updatedProduct2.status).isEqualTo(ProductSaleStatus.ON_SALE) },
            )
        }

        @DisplayName("재고가 0이 되어도 정상 처리된다")
        @Test
        fun `handle zero stock successfully`() {
            // given
            val product = createProduct(stockQuantity = 10)

            // when
            val command = ProductCommand.DecreaseStocks(
                units = listOf(
                    ProductCommand.DecreaseStockUnit(product.id, 10),
                ),
            )
            productService.decreaseStocks(command)

            // then
            val updatedStock = stockRepository.findByProductId(product.id)!!
            val updatedProduct = productRepository.findById(product.id)!!
            assertAll(
                { assertThat(updatedStock.quantity).isEqualTo(0) },
                { assertThat(updatedProduct.status).isEqualTo(ProductSaleStatus.SOLD_OUT) },
            )
        }

        @DisplayName("재고가 부족하면 예외가 발생한다")
        @Test
        fun `throw exception when stock is insufficient`() {
            // given
            val product = createProduct(stockQuantity = 10)

            // when & then
            val command = ProductCommand.DecreaseStocks(
                units = listOf(
                    ProductCommand.DecreaseStockUnit(product.id, 20),
                ),
            )
            val exception = assertThrows<CoreException> {
                productService.decreaseStocks(command)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("재고가 부족합니다")
        }

        @DisplayName("재고 감소 실패 시 모든 상품의 재고가 롤백된다")
        @Test
        fun `rollback all stock changes when one product fails`() {
            // given
            val product1 = createProduct(name = "상품1", stockQuantity = 100)
            val product2 = createProduct(name = "상품2", stockQuantity = 5)

            // when
            val command = ProductCommand.DecreaseStocks(
                units = listOf(
                    ProductCommand.DecreaseStockUnit(product1.id, 10),
                    ProductCommand.DecreaseStockUnit(product2.id, 10),
                ),
            )
            assertThrows<CoreException> {
                productService.decreaseStocks(command)
            }

            // then
            val unchangedStock1 = stockRepository.findByProductId(product1.id)!!
            val unchangedStock2 = stockRepository.findByProductId(product2.id)!!
            assertAll(
                { assertThat(unchangedStock1.quantity).isEqualTo(100) },
                { assertThat(unchangedStock2.quantity).isEqualTo(5) },
            )
        }
    }

    @DisplayName("재고 증가 통합테스트")
    @Nested
    inner class IncreaseStocks {

        @DisplayName("재고를 복구할 수 있다")
        @Test
        fun `increase stock successfully`() {
            // given
            val brand = brandRepository.save(Brand.of("브랜드"))
            val product = createProduct(brandId = brand.id, stockQuantity = 50)

            val command = ProductCommand.IncreaseStocks(
                units = listOf(
                    ProductCommand.IncreaseStockUnit(productId = product.id, amount = 30),
                ),
            )

            // when
            productService.increaseStocks(command)

            // then
            val updatedStock = stockRepository.findByProductId(product.id)
            assertThat(updatedStock?.quantity).isEqualTo(80)
        }

        @DisplayName("여러 상품의 재고를 한 번에 복구할 수 있다")
        @Test
        fun `increase stocks for multiple products`() {
            // given
            val brand = brandRepository.save(Brand.of("브랜드"))
            val product1 = createProduct(brandId = brand.id, name = "상품1", stockQuantity = 10)
            val product2 = createProduct(brandId = brand.id, name = "상품2", stockQuantity = 20)

            val command = ProductCommand.IncreaseStocks(
                units = listOf(
                    ProductCommand.IncreaseStockUnit(productId = product1.id, amount = 5),
                    ProductCommand.IncreaseStockUnit(productId = product2.id, amount = 15),
                ),
            )

            // when
            productService.increaseStocks(command)

            // then
            assertAll(
                { assertThat(stockRepository.findByProductId(product1.id)?.quantity).isEqualTo(15) },
                { assertThat(stockRepository.findByProductId(product2.id)?.quantity).isEqualTo(35) },
            )
        }

        @DisplayName("재고가 0에서 증가하면 상품 상태가 ON_SALE로 변경된다")
        @Test
        fun `update product status to ON_SALE when stock increases from zero`() {
            // given
            val brand = brandRepository.save(Brand.of("브랜드"))
            val product = createProduct(brandId = brand.id, stockQuantity = 10)

            // 재고를 0으로 감소시킴
            val decreaseCommand = ProductCommand.DecreaseStocks(
                units = listOf(
                    ProductCommand.DecreaseStockUnit(productId = product.id, amount = 10),
                ),
            )
            productService.decreaseStocks(decreaseCommand)

            // 상품이 SOLD_OUT 상태인지 확인
            val soldOutProduct = productRepository.findById(product.id)!!
            assertThat(soldOutProduct.status).isEqualTo(ProductSaleStatus.SOLD_OUT)

            // when
            val increaseCommand = ProductCommand.IncreaseStocks(
                units = listOf(
                    ProductCommand.IncreaseStockUnit(productId = product.id, amount = 5),
                ),
            )
            productService.increaseStocks(increaseCommand)

            // then
            val updatedStock = stockRepository.findByProductId(product.id)
            val updatedProduct = productRepository.findById(product.id)!!
            assertAll(
                { assertThat(updatedStock?.quantity).isEqualTo(5) },
                { assertThat(updatedProduct.status).isEqualTo(ProductSaleStatus.ON_SALE) },
            )
        }
    }

    @DisplayName("여러 상품 상세 조회 통합테스트")
    @Nested
    inner class FindAllProductViewByIds {

        @DisplayName("여러 상품을 조회하면 모든 상품의 상세 정보가 반환된다")
        @Test
        fun `return all product views when products exist`() {
            // given
            val product1 = createProduct(name = "상품1")
            val product2 = createProduct(name = "상품2")
            val product3 = createProduct(name = "상품3")
            val productIds = listOf(product1.id, product2.id, product3.id)

            // when
            val productViews = productService.findAllProductViewByIds(productIds)

            // then
            assertThat(productViews).hasSize(3)
            assertThat(productViews.map { it.productId }).containsExactlyInAnyOrder(
                product1.id,
                product2.id,
                product3.id,
            )
            productViews.forEach { productView ->
                assertAll(
                    { assertThat(productView.productId).isNotNull() },
                    { assertThat(productView.stockQuantity).isNotNull() },
                    { assertThat(productView.likeCount).isNotNull() },
                    { assertThat(productView.brandId).isNotNull() },
                )
            }
        }

        @DisplayName("빈 리스트를 전달하면 빈 리스트가 반환된다")
        @Test
        fun `return empty list when ids is empty`() {
            // when
            val productViews = productService.findAllProductViewByIds(emptyList())

            // then
            assertThat(productViews).isEmpty()
        }

        @DisplayName("존재하지 않는 상품이 포함되면 예외가 발생한다")
        @Test
        fun `throw exception when some products not exist`() {
            // given
            val product1 = createProduct(name = "상품1")
            val nonExistentId = 999L
            val productIds = listOf(product1.id, nonExistentId)

            // when
            val exception = assertThrows<CoreException> {
                productService.findAllProductViewByIds(productIds)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("존재하지 않는 상품입니다")
            assertThat(exception.message).contains(nonExistentId.toString())
        }

        @DisplayName("중복된 ID를 전달하면 중복 없이 조회된다")
        @Test
        fun `handle duplicate ids correctly`() {
            // given
            val product = createProduct(name = "상품")
            val productIds = listOf(product.id, product.id, product.id)

            // when
            val productViews = productService.findAllProductViewByIds(productIds)

            // then
            assertThat(productViews).hasSize(1)
            assertThat(productViews[0].productId).isEqualTo(product.id)
        }
    }

    @DisplayName("상품 좋아요 수 증가 통합테스트")
    @Nested
    inner class IncreaseProductLikeCount {

        @DisplayName("상품의 좋아요 수를 증가시킬 수 있다")
        @Test
        fun `increase product like count`() {
            // given
            val product = createProduct()
            val initialLikeCount = getProductLikeCount(product.id)

            // when
            productService.increaseProductLikeCount(product.id)

            // then
            val updatedLikeCount = getProductLikeCount(product.id)
            assertThat(updatedLikeCount).isEqualTo(initialLikeCount + 1)
        }
    }

    @DisplayName("상품 좋아요 수 감소 통합테스트")
    @Nested
    inner class DecreaseProductLikeCount {

        @DisplayName("상품의 좋아요 수를 감소시킬 수 있다")
        @Test
        fun `decrease product like count`() {
            // given
            val product = createProduct()
            productService.increaseProductLikeCount(product.id)
            val beforeLikeCount = getProductLikeCount(product.id)

            // when
            productService.decreaseProductLikeCount(product.id)

            // then
            val updatedLikeCount = getProductLikeCount(product.id)
            assertThat(updatedLikeCount).isEqualTo(beforeLikeCount - 1)
        }
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
        brandId: Long? = null,
    ): Product {
        val brand = if (brandId != null) {
            brandRepository.findById(brandId)!!
        } else {
            brandRepository.save(Brand.create("테스트 브랜드"))
        }

        val product = Product.create(
            name = name,
            price = price,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun getProductLikeCount(productId: Long): Long {
        return productStatisticRepository.findByProductId(productId)?.likeCount ?: 0L
    }
}
