package com.loopers.domain.product

import com.loopers.IntegrationTestSupport
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderItemCommand
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.signal.ProductTotalSignalJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class ProductServiceTest(
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val productTotalSignalRepository: ProductTotalSignalJpaRepository,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("리스트 조회")
    @Nested
    inner class GetAllProducts {

        @DisplayName("상품 목록이 최신순(latest)으로 조회된다")
        @Test
        fun getAllProductsSuccess_sortByLatest() {
            // arrange
            val brand = BrandModel("Nike")
            brandRepository.save(brand)

            val product1 = ProductModel.create(
                name = "First Product",
                stock = 100,
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)

            Thread.sleep(10) // 시간 차이를 위해

            val product2 = ProductModel.create(
                name = "Second Product",
                stock = 50,
                price = Money(BigDecimal.valueOf(20000)),
                refBrandId = brand.id,
            )
            productRepository.save(product2)

            Thread.sleep(10)

            val product3 = ProductModel.create(
                name = "Third Product",
                stock = 30,
                price = Money(BigDecimal.valueOf(15000)),
                refBrandId = brand.id,
            )
            productRepository.save(product3)

            // ProductTotalSignal 생성
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product1.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product2.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product3.id))

            // act
            val result = productService.getProducts("createdAt", "desc", 0, 10)

            // assert
            assertThat(result.content).hasSize(3)
            assertThat(result.totalElements).isEqualTo(3)
            assertThat(result.content[0].name).isEqualTo("Third Product") // 가장 최근
            assertThat(result.content[1].name).isEqualTo("Second Product")
            assertThat(result.content[2].name).isEqualTo("First Product")
        }

        @DisplayName("상품 목록이 가격 오름차순(price_asc)으로 조회된다")
        @Test
        fun getAllProductsSuccess_sortByPriceAsc() {
            // arrange
            val brand = BrandModel("Adidas")
            brandRepository.save(brand)

            val product1 = ProductModel.create(
                name = "Expensive Product",
                stock = 10,
                price = Money(BigDecimal.valueOf(50000)),
                refBrandId = brand.id,
            )
            val product2 = ProductModel.create(
                name = "Cheap Product",
                stock = 100,
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            val product3 = ProductModel.create(
                name = "Medium Product",
                stock = 50,
                price = Money(BigDecimal.valueOf(30000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            productRepository.save(product2)
            productRepository.save(product3)

            // ProductTotalSignal 생성
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product1.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product2.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product3.id))

            // act
            val result = productService.getProducts("price", "asc", 0, 10)

            // assert
            assertThat(result.content).hasSize(3)
            assertThat(result.content[0].name).isEqualTo("Cheap Product")
            assertThat(result.content[0].price).isEqualByComparingTo(BigDecimal.valueOf(10000))
            assertThat(result.content[1].name).isEqualTo("Medium Product")
            assertThat(result.content[1].price).isEqualByComparingTo(BigDecimal.valueOf(30000))
            assertThat(result.content[2].name).isEqualTo("Expensive Product")
            assertThat(result.content[2].price).isEqualByComparingTo(BigDecimal.valueOf(50000))
        }

        @DisplayName("상품 목록이 좋아요 내림차순(likes_desc)으로 조회된다")
        @Test
        fun getAllProductsSuccess_sortByLikesDesc() {
            // arrange
            val brand = BrandModel("Puma")
            brandRepository.save(brand)

            val product1 = ProductModel.create(
                name = "Unpopular Product",
                stock = 100,
                price = Money(BigDecimal.valueOf(20000)),
                refBrandId = brand.id,
            )
            val product2 = ProductModel.create(
                name = "Popular Product",
                stock = 50,
                price = Money(BigDecimal.valueOf(25000)),
                refBrandId = brand.id,
            )
            val product3 = ProductModel.create(
                name = "Medium Popular Product",
                stock = 70,
                price = Money(BigDecimal.valueOf(22000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            productRepository.save(product2)
            productRepository.save(product3)

            // ProductTotalSignal 생성 및 좋아요 수 설정
            val signal1 = ProductTotalSignalModel(refProductId = product1.id).apply {
                incrementLikeCount() // 1개
            }
            val signal2 = ProductTotalSignalModel(refProductId = product2.id).apply {
                repeat(5) { incrementLikeCount() } // 5개
            }
            val signal3 = ProductTotalSignalModel(refProductId = product3.id).apply {
                repeat(3) { incrementLikeCount() } // 3개
            }
            productTotalSignalRepository.save(signal1)
            productTotalSignalRepository.save(signal2)
            productTotalSignalRepository.save(signal3)

            // act
            val result = productService.getProducts("likeCount", "desc", 0, 10)

            // assert
            assertThat(result.content).hasSize(3)
            assertThat(result.content[0].name).isEqualTo("Popular Product")
            assertThat(result.content[0].likeCount).isEqualTo(5)
            assertThat(result.content[1].name).isEqualTo("Medium Popular Product")
            assertThat(result.content[1].likeCount).isEqualTo(3)
            assertThat(result.content[2].name).isEqualTo("Unpopular Product")
            assertThat(result.content[2].likeCount).isEqualTo(1)
        }

        @DisplayName("상품이 없을 때 빈 리스트가 반환된다")
        @Test
        fun getAllProductsSuccess_whenEmpty() {
            // act
            val result = productService.getProducts("createdAt", "desc", 0, 10)

            // assert
            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
            assertThat(result.totalPages).isEqualTo(0)
        }
    }

    @DisplayName("재고를 점유")
    @Nested
    inner class OccupyStock {

        @DisplayName("재고가 충분한 경우, 재고 점유에 성공한다.")
        @Test
        fun occupyStockSuccess() {
            // arrange
            val brand = BrandModel("Nike")
            brandRepository.save(brand)

            val product = ProductModel.create(
                name = "Test Product",
                stock = 100,
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(
                        productId = product.id,
                        quantity = 30,
                        productPrice = BigDecimal.valueOf(10000),
                    ),
                ),
            )

            // act
            productService.occupyStocks(orderCommand)

            // assert
            val updatedProduct = productRepository.findById(product.id).get()
            assertThat(updatedProduct.stock).isEqualTo(70)
        }

        @DisplayName("여러 상품의 재고를 동시에 점유할 수 있다.")
        @Test
        fun occupyMultipleStocksSuccess() {
            // arrange
            val brand = BrandModel("Adidas")
            brandRepository.save(brand)

            val product1 = ProductModel.create(
                name = "Product 1",
                stock = 100,
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            val product2 = ProductModel.create(
                name = "Product 2",
                stock = 50,
                price = Money(BigDecimal.valueOf(20000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            productRepository.save(product2)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = product1.id, quantity = 10, productPrice = BigDecimal.valueOf(10000)),
                    OrderItemCommand(productId = product2.id, quantity = 5, productPrice = BigDecimal.valueOf(20000)),
                ),
            )

            // act
            productService.occupyStocks(orderCommand)

            // assert
            val updatedProduct1 = productRepository.findById(product1.id).get()
            val updatedProduct2 = productRepository.findById(product2.id).get()
            assertThat(updatedProduct1.stock).isEqualTo(90)
            assertThat(updatedProduct2.stock).isEqualTo(45)
        }

        @DisplayName("재고가 부족한 경우, 예외가 발생한다.")
        @Test
        fun occupyStockFails_whenInsufficientStock() {
            // arrange
            val brand = BrandModel("Puma")
            brandRepository.save(brand)

            val product = ProductModel.create(
                name = "Low Stock Product",
                stock = 5,
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = product.id, quantity = 10, productPrice = BigDecimal.valueOf(10000)),
                ),
            )

            // act, assert
            val exception = assertThrows<IllegalArgumentException> {
                productService.occupyStocks(orderCommand)
            }
            assertThat(exception.message).isEqualTo("재고가 부족합니다.")
        }

        @DisplayName("존재하지 않는 상품 ID로 재고 점유 시도 시, 예외가 발생한다.")
        @Test
        fun occupyStockFails_whenProductNotExists() {
            // arrange
            val notExistProductId = 999L
            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = notExistProductId, quantity = 1, productPrice = BigDecimal.valueOf(10000)),
                ),
            )

            // act, assert
            val exception = assertThrows<CoreException> {
                productService.occupyStocks(orderCommand)
            }
            assertThat(exception.message).isEqualTo("존재하지 않는 상품입니다.")
        }

        @DisplayName("0 이하의 수량으로 재고 점유 시도 시, 예외가 발생한다.")
        @Test
        fun occupyStockFails_whenQuantityIsZeroOrNegative() {
            // arrange
            val brand = BrandModel("Reebok")
            brandRepository.save(brand)

            val product = ProductModel.create(
                name = "Test Product",
                stock = 100,
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = product.id, quantity = 0, productPrice = BigDecimal.valueOf(10000)),
                ),
            )

            // act, assert
            val exception = assertThrows<IllegalArgumentException> {
                productService.occupyStocks(orderCommand)
            }
            assertThat(exception.message).isEqualTo("감소 수량은 0보다 커야 합니다.")
        }
    }
}
