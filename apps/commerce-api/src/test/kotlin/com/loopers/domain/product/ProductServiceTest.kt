package com.loopers.domain.product

import com.loopers.IntegrationTestSupport
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderItemCommand
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.signal.ProductTotalSignalModel
import com.loopers.domain.product.stock.StockModel
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.signal.ProductTotalSignalJpaRepository
import com.loopers.infrastructure.product.stock.StockJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal

class ProductServiceTest(
    private val productService: ProductService,
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val productTotalSignalRepository: ProductTotalSignalJpaRepository,
    private val stockRepository: StockJpaRepository,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("리스트 조회")
    @Nested
    inner class GetAllProducts {

        @DisplayName("브랜드 필터로 조회된다.")
        @Test
        fun getAllProductsSuccess_filterByBrand() {
            // arrange
            val brand1 = BrandModel("Nike")
            val brand2 = BrandModel("Adidas")
            brandRepository.save(brand1)
            brandRepository.save(brand2)

            // Nike 상품 3개
            val nikeProduct1 = ProductModel.create(
                name = "Nike Air Max",
                price = Money(BigDecimal.valueOf(150000)),
                refBrandId = brand1.id,
            )
            val nikeProduct2 = ProductModel.create(
                name = "Nike Jordan",
                price = Money(BigDecimal.valueOf(200000)),
                refBrandId = brand1.id,
            )
            val nikeProduct3 = ProductModel.create(
                name = "Nike Dunk",
                price = Money(BigDecimal.valueOf(120000)),
                refBrandId = brand1.id,
            )
            productRepository.save(nikeProduct1)
            productRepository.save(nikeProduct2)
            productRepository.save(nikeProduct3)

            // Adidas 상품 2개
            val adidasProduct1 = ProductModel.create(
                name = "Adidas Superstar",
                price = Money(BigDecimal.valueOf(100000)),
                refBrandId = brand2.id,
            )
            val adidasProduct2 = ProductModel.create(
                name = "Adidas Stan Smith",
                price = Money(BigDecimal.valueOf(90000)),
                refBrandId = brand2.id,
            )
            productRepository.save(adidasProduct1)
            productRepository.save(adidasProduct2)

            // 재고 및 시그널 생성
            listOf(nikeProduct1, nikeProduct2, nikeProduct3, adidasProduct1, adidasProduct2).forEach { product ->
                stockRepository.save(StockModel.create(product.id, 100))
                productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product.id))
            }

            // act - Nike 브랜드만 필터링
            val result = productService.getProducts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")),
                brand1.id,
            )

            // assert
            assertThat(result.content).hasSize(3)
            assertThat(result.totalElements).isEqualTo(3)
            assertThat(result.content).allMatch { it.brandId == brand1.id }
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                "Nike Air Max",
                "Nike Jordan",
                "Nike Dunk",
            )
        }

        @DisplayName("브랜드 필터 + 좋아요 내림차순으로 조회된다")
        @Test
        fun getAllProductsSuccess_filterByBrandAndSortByLikes() {
            // arrange
            val brand1 = BrandModel("Nike")
            val brand2 = BrandModel("Adidas")
            brandRepository.save(brand1)
            brandRepository.save(brand2)

            // Nike 상품 3개
            val nikeProduct1 = ProductModel.create(
                name = "Nike Air Max",
                price = Money(BigDecimal.valueOf(150000)),
                refBrandId = brand1.id,
            )
            val nikeProduct2 = ProductModel.create(
                name = "Nike Jordan",
                price = Money(BigDecimal.valueOf(200000)),
                refBrandId = brand1.id,
            )
            val nikeProduct3 = ProductModel.create(
                name = "Nike Dunk",
                price = Money(BigDecimal.valueOf(120000)),
                refBrandId = brand1.id,
            )
            productRepository.save(nikeProduct1)
            productRepository.save(nikeProduct2)
            productRepository.save(nikeProduct3)

            // Adidas 상품 2개
            val adidasProduct1 = ProductModel.create(
                name = "Adidas Superstar",
                price = Money(BigDecimal.valueOf(100000)),
                refBrandId = brand2.id,
            )
            val adidasProduct2 = ProductModel.create(
                name = "Adidas Stan Smith",
                price = Money(BigDecimal.valueOf(90000)),
                refBrandId = brand2.id,
            )
            productRepository.save(adidasProduct1)
            productRepository.save(adidasProduct2)

            // 재고 생성
            listOf(nikeProduct1, nikeProduct2, nikeProduct3, adidasProduct1, adidasProduct2).forEach { product ->
                stockRepository.save(StockModel.create(product.id, 100))
            }

            // ProductTotalSignal 생성 및 좋아요 수 설정
            // Nike Air Max: 10개 (가장 많음)
            val nikeSignal1 = ProductTotalSignalModel(refProductId = nikeProduct1.id).apply {
                repeat(10) { incrementLikeCount() }
            }
            // Nike Jordan: 3개
            val nikeSignal2 = ProductTotalSignalModel(refProductId = nikeProduct2.id).apply {
                repeat(3) { incrementLikeCount() }
            }
            // Nike Dunk: 7개
            val nikeSignal3 = ProductTotalSignalModel(refProductId = nikeProduct3.id).apply {
                repeat(7) { incrementLikeCount() }
            }
            // Adidas 상품들 (필터링되어 결과에 포함되지 않음)
            val adidasSignal1 = ProductTotalSignalModel(refProductId = adidasProduct1.id).apply {
                repeat(15) { incrementLikeCount() } // Nike보다 많지만 필터링됨
            }
            val adidasSignal2 = ProductTotalSignalModel(refProductId = adidasProduct2.id).apply {
                repeat(5) { incrementLikeCount() }
            }
            productTotalSignalRepository.save(nikeSignal1)
            productTotalSignalRepository.save(nikeSignal2)
            productTotalSignalRepository.save(nikeSignal3)
            productTotalSignalRepository.save(adidasSignal1)
            productTotalSignalRepository.save(adidasSignal2)

            // act - Nike 브랜드만 필터링 + 좋아요 내림차순
            val result = productService.getProducts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "likeCount")),
                brand1.id,
            )

            // assert
            assertThat(result.content).hasSize(3)
            assertThat(result.totalElements).isEqualTo(3)
            assertThat(result.content).allMatch { it.brandId == brand1.id }
            // 좋아요 순서: Nike Air Max (10) > Nike Dunk (7) > Nike Jordan (3)
            assertThat(result.content[0].name).isEqualTo("Nike Air Max")
            assertThat(result.content[0].likeCount).isEqualTo(10)
            assertThat(result.content[1].name).isEqualTo("Nike Dunk")
            assertThat(result.content[1].likeCount).isEqualTo(7)
            assertThat(result.content[2].name).isEqualTo("Nike Jordan")
            assertThat(result.content[2].likeCount).isEqualTo(3)
        }

        @DisplayName("상품 목록이 최신순(latest)으로 조회된다")
        @Test
        fun getAllProductsSuccess_sortByLatest() {
            // arrange
            val brand = BrandModel("Nike")
            brandRepository.save(brand)

            val product1 = ProductModel.create(
                name = "First Product",
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            stockRepository.save(StockModel.create(product1.id, 100))

            val product2 = ProductModel.create(
                name = "Second Product",
                price = Money(BigDecimal.valueOf(20000)),
                refBrandId = brand.id,
            )
            productRepository.save(product2)
            stockRepository.save(StockModel.create(product2.id, 50))

            val product3 = ProductModel.create(
                name = "Third Product",
                price = Money(BigDecimal.valueOf(15000)),
                refBrandId = brand.id,
            )
            productRepository.save(product3)
            stockRepository.save(StockModel.create(product3.id, 30))

            // ProductTotalSignal 생성
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product1.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product2.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product3.id))

            // act
            val result = productService.getProducts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")),
                null,
            )

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
                price = Money(BigDecimal.valueOf(50000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            stockRepository.save(StockModel.create(product1.id, 10))

            val product2 = ProductModel.create(
                name = "Cheap Product",
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product2)
            stockRepository.save(StockModel.create(product2.id, 100))

            val product3 = ProductModel.create(
                name = "Medium Product",
                price = Money(BigDecimal.valueOf(30000)),
                refBrandId = brand.id,
            )
            productRepository.save(product3)
            stockRepository.save(StockModel.create(product3.id, 50))

            // ProductTotalSignal 생성
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product1.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product2.id))
            productTotalSignalRepository.save(ProductTotalSignalModel(refProductId = product3.id))

            // act
            val result = productService.getProducts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price")),
                null,
            )

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
                price = Money(BigDecimal.valueOf(20000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            stockRepository.save(StockModel.create(product1.id, 100))

            val product2 = ProductModel.create(
                name = "Popular Product",
                price = Money(BigDecimal.valueOf(25000)),
                refBrandId = brand.id,
            )
            productRepository.save(product2)
            stockRepository.save(StockModel.create(product2.id, 50))

            val product3 = ProductModel.create(
                name = "Medium Popular Product",
                price = Money(BigDecimal.valueOf(22000)),
                refBrandId = brand.id,
            )
            productRepository.save(product3)
            stockRepository.save(StockModel.create(product3.id, 70))

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
            val result = productService.getProducts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "likeCount")),
                null,
            )

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
            val result = productService.getProducts(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")),
                null,
            )

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
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product)

            val stock = StockModel.create(product.id, 100)
            stockRepository.save(stock)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(
                        productId = product.id,
                        quantity = 30,
                        productPrice = BigDecimal.valueOf(10000),
                    ),
                ),
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = null
            )

            // act
            productService.occupyStocks(orderCommand)

            // assert
            val updatedStock = stockRepository.findByRefProductId(product.id)!!
            assertThat(updatedStock.amount).isEqualTo(70)
        }

        @DisplayName("여러 상품의 재고를 동시에 점유할 수 있다.")
        @Test
        fun occupyMultipleStocksSuccess() {
            // arrange
            val brand = BrandModel("Adidas")
            brandRepository.save(brand)

            val product1 = ProductModel.create(
                name = "Product 1",
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product1)
            stockRepository.save(StockModel.create(product1.id, 100))

            val product2 = ProductModel.create(
                name = "Product 2",
                price = Money(BigDecimal.valueOf(20000)),
                refBrandId = brand.id,
            )
            productRepository.save(product2)
            stockRepository.save(StockModel.create(product2.id, 50))

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = product1.id, quantity = 10, productPrice = BigDecimal.valueOf(10000)),
                    OrderItemCommand(productId = product2.id, quantity = 5, productPrice = BigDecimal.valueOf(20000)),
                ),
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = null
            )

            // act
            productService.occupyStocks(orderCommand)

            // assert
            val updatedStock1 = stockRepository.findByRefProductId(product1.id)!!
            val updatedStock2 = stockRepository.findByRefProductId(product2.id)!!
            assertThat(updatedStock1.amount).isEqualTo(90)
            assertThat(updatedStock2.amount).isEqualTo(45)
        }

        @DisplayName("재고가 부족한 경우, 예외가 발생한다.")
        @Test
        fun occupyStockFails_whenInsufficientStock() {
            // arrange
            val brand = BrandModel("Puma")
            brandRepository.save(brand)

            val product = ProductModel.create(
                name = "Low Stock Product",
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product)

            val stock = StockModel.create(product.id, 5)
            stockRepository.save(stock)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = product.id, quantity = 10, productPrice = BigDecimal.valueOf(10000)),
                ),
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = null
            )

            // act, assert
            val exception = assertThrows<CoreException> {
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
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = null
            )

            // act, assert
            val exception = assertThrows<CoreException> {
                productService.occupyStocks(orderCommand)
            }
            assertThat(exception.message).isEqualTo("재고가 존재하지 않습니다.")
        }

        @DisplayName("0 이하의 수량으로 재고 점유 시도 시, 예외가 발생한다.")
        @Test
        fun occupyStockFails_whenQuantityIsZeroOrNegative() {
            // arrange
            val brand = BrandModel("Reebok")
            brandRepository.save(brand)

            val product = ProductModel.create(
                name = "Test Product",
                price = Money(BigDecimal.valueOf(10000)),
                refBrandId = brand.id,
            )
            productRepository.save(product)

            val stock = StockModel.create(product.id, 100)
            stockRepository.save(stock)

            val orderCommand = OrderCommand(
                orderItems = listOf(
                    OrderItemCommand(productId = product.id, quantity = 0, productPrice = BigDecimal.valueOf(10000)),
                ),
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = null
            )

            // act, assert
            val exception = assertThrows<CoreException> {
                productService.occupyStocks(orderCommand)
            }
            assertThat(exception.message).isEqualTo("차감 수량은 0보다 커야 합니다.")
        }
    }
}
