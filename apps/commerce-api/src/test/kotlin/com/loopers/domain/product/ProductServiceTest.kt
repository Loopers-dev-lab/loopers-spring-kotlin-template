package com.loopers.domain.product

import com.loopers.IntegrationTest
import com.loopers.domain.brand.Brand
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderDetail
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.StockJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionTemplate

class ProductServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    private lateinit var productJpaRepository: ProductJpaRepository

    @Autowired
    private lateinit var brandJpaRepository: BrandJpaRepository

    @Autowired
    private lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    private lateinit var stockJpaRepository: StockJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Nested
    @DisplayName("getProducts")
    inner class GetProducts {

        @Test
        fun `브랜드 ID 없이 전체 상품 목록을 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            val pageable = PageRequest.of(0, 10)

            // when
            val result = productService.getProducts(null, ProductSort.LATEST, pageable)

            // then
            assertSoftly { soft ->
                soft.assertThat(result.content).hasSize(2)
                soft.assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(product1.id, product2.id)
            }
        }

        @Test
        fun `특정 브랜드의 상품 목록을 조회한다`() {
            // given
            val brand1 = createAndSaveBrand("브랜드1")
            val brand2 = createAndSaveBrand("브랜드2")
            val product1 = createAndSaveProduct("상품1", 10000L, brand1.id)
            createAndSaveProduct("상품2", 20000L, brand2.id)
            val pageable = PageRequest.of(0, 10)

            // when
            val result = productService.getProducts(brand1.id, ProductSort.LATEST, pageable)

            // then
            assertSoftly { soft ->
                soft.assertThat(result.content).hasSize(1)
                soft.assertThat(result.content[0].id).isEqualTo(product1.id)
                soft.assertThat(result.content[0].brandId).isEqualTo(brand1.id)
            }
        }

        @Test
        fun `가격 오름차순으로 상품 목록을 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            createAndSaveProduct("상품1", 30000L, brand.id)
            createAndSaveProduct("상품2", 10000L, brand.id)
            createAndSaveProduct("상품3", 20000L, brand.id)
            val pageable = PageRequest.of(0, 10)

            // when
            val result = productService.getProducts(null, ProductSort.PRICE_ASC, pageable)

            // then
            assertSoftly { soft ->
                soft.assertThat(result.content).hasSize(3)
                soft.assertThat(result.content.map { it.price })
                    .containsExactly(10000L, 20000L, 30000L)
            }
        }

        @Test
        fun `페이징을 적용하여 상품 목록을 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            repeat(15) { i ->
                createAndSaveProduct("상품${i + 1}", 10000L + i * 1000, brand.id)
            }
            val pageable = PageRequest.of(0, 10)

            // when
            val result = productService.getProducts(null, ProductSort.LATEST, pageable)

            // then
            assertSoftly { soft ->
                soft.assertThat(result.content).hasSize(10)
                soft.assertThat(result.totalElements).isEqualTo(15)
                soft.assertThat(result.totalPages).isEqualTo(2)
            }
        }
    }

    @Nested
    @DisplayName("getProduct")
    inner class GetProduct {

        @Test
        fun `상품 ID로 상품을 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product = createAndSaveProduct("테스트상품", 10000L, brand.id)

            // when
            val result = productService.getProduct(product.id)

            // then
            assertSoftly { soft ->
                soft.assertThat(result).isNotNull
                soft.assertThat(result!!.id).isEqualTo(product.id)
                soft.assertThat(result.name).isEqualTo("테스트상품")
                soft.assertThat(result.price).isEqualTo(10000L)
            }
        }

        @Test
        fun `존재하지 않는 상품 ID로 조회하면 null을 반환한다`() {
            // when
            val result = productService.getProduct(999L)

            // then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getProducts with ids")
    inner class GetProductsWithIds {

        @Test
        fun `여러 상품 ID로 상품 목록을 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            val product3 = createAndSaveProduct("상품3", 30000L, brand.id)
            val productIds = listOf(product1.id, product2.id, product3.id)

            // when
            val result = productService.getProducts(productIds)

            // then
            assertSoftly { soft ->
                soft.assertThat(result).hasSize(3)
                soft.assertThat(result.map { it.id })
                    .containsExactlyInAnyOrder(product1.id, product2.id, product3.id)
            }
        }

        @Test
        fun `존재하지 않는 상품 ID가 포함되어도 존재하는 상품만 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val productIds = listOf(product1.id, 999L)

            // when
            val result = productService.getProducts(productIds)

            // then
            assertSoftly { soft ->
                soft.assertThat(result).hasSize(1)
                soft.assertThat(result[0].id).isEqualTo(product1.id)
            }
        }

        @Test
        fun `빈 ID 목록으로 조회하면 빈 리스트를 반환한다`() {
            // when
            val result = productService.getProducts(emptyList())

            // then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("getStocksBy")
    inner class GetStocksBy {

        @Test
        fun `여러 상품의 재고를 조회한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            val stock1 = createAndSaveStock(100L, product1.id)
            val stock2 = createAndSaveStock(200L, product2.id)
            val productIds = listOf(product1.id, product2.id)

            // when
            val result = productService.getStocksBy(productIds)

            // then
            assertSoftly { soft ->
                soft.assertThat(result).hasSize(2)
                soft.assertThat(result.map { it.productId })
                    .containsExactlyInAnyOrder(product1.id, product2.id)
                soft.assertThat(result.find { it.id == stock1.id }?.quantity).isEqualTo(100L)
                soft.assertThat(result.find { it.id == stock2.id }?.quantity).isEqualTo(200L)
            }
        }

        @Test
        fun `재고가 없는 상품은 결과에 포함되지 않는다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            createAndSaveStock(100L, product1.id)
            val productIds = listOf(product1.id, product2.id)

            // when
            val result = productService.getStocksBy(productIds)

            // then
            assertSoftly { soft ->
                soft.assertThat(result).hasSize(1)
                soft.assertThat(result[0].productId).isEqualTo(product1.id)
            }
        }
    }

    @Nested
    @DisplayName("validateProductsExist")
    inner class ValidateProductsExist {

        @Test
        fun `모든 상품이 존재하면 검증을 통과한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            val products = listOf(product1, product2)

            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = product1.id, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = product2.id, quantity = 5),
            )

            // when & then
            assertThatCode {
                productService.validateProductsExist(items, products)
            }.doesNotThrowAnyException()
        }

        @Test
        fun `여러 상품이 존재하지 않으면 모두 포함하여 예외가 발생한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val products = listOf(product1)

            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = product1.id, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = 998L, quantity = 5),
                OrderCommand.OrderDetailCommand(productId = 999L, quantity = 3),
            )

            // when & then
            assertThatThrownBy {
                productService.validateProductsExist(items, products)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("998")
                .hasMessageContaining("999")
        }

        @Test
        fun `중복된 상품 ID가 있어도 한 번만 검증한다`() {
            // given
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val products = listOf(product1)

            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = product1.id, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = product1.id, quantity = 5),
                OrderCommand.OrderDetailCommand(productId = product1.id, quantity = 3),
            )

            // when & then
            assertThatCode {
                productService.validateProductsExist(items, products)
            }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("deductAllStock")
    inner class DeductAllStock {

        @Test
        fun `여러 상품의 재고를 차감하고 DB에 저장한다`() {
            // given
            val userId = 1L
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            val stock1 = createAndSaveStock(100L, product1.id)
            val stock2 = createAndSaveStock(200L, product2.id)
            val order = createAndSaveOrder(5000000L, userId)

            val orderDetails = listOf(
                OrderDetail.create(10L, brand, product1, order),
                OrderDetail.create(20L, brand, product2, order),
            )

            // when
            transactionTemplate.execute {
                productService.deductAllStock(orderDetails)
            }

            // then
            val updatedStock1 = stockJpaRepository.findById(stock1.id).get()
            val updatedStock2 = stockJpaRepository.findById(stock2.id).get()

            assertSoftly { soft ->
                soft.assertThat(updatedStock1.quantity).isEqualTo(90L)
                soft.assertThat(updatedStock2.quantity).isEqualTo(180L)
            }
        }

        @Test
        fun `동일 상품에 대한 중복 주문 항목 수량을 합산하여 차감한다`() {
            // given
            val userId = 1L
            val brand = createAndSaveBrand("테스트브랜드")
            val product = createAndSaveProduct("상품1", 10000L, brand.id)
            val stock = createAndSaveStock(100L, product.id)
            val order = createAndSaveOrder(5000000L, userId)

            val orderDetails = listOf(
                OrderDetail.create(10L, brand, product, order),
                OrderDetail.create(20L, brand, product, order),
                OrderDetail.create(30L, brand, product, order),
            )

            // when
            transactionTemplate.execute {
                productService.deductAllStock(orderDetails)
            }

            // then
            val updatedStock = stockJpaRepository.findById(stock.id).get()
            assertThat(updatedStock.quantity).isEqualTo(40L)
        }

        @Test
        fun `재고가 부족하면 예외가 발생한다`() {
            // given
            val userId = 1L
            val brand = createAndSaveBrand("테스트브랜드")
            val product = createAndSaveProduct("상품1", 10000L, brand.id)
            createAndSaveStock(10L, product.id)
            val order = createAndSaveOrder(5000000L, userId)

            val orderDetails = listOf(
                OrderDetail.create(20L, brand, product, order),
            )

            // when & then
            assertThatThrownBy {
                productService.deductAllStock(orderDetails)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
        }

        @Test
        fun `재고 차감 중 예외가 발생하면 DB에 반영되지 않는다`() {
            // given
            val userId = 1L
            val brand = createAndSaveBrand("테스트브랜드")
            val product1 = createAndSaveProduct("상품1", 10000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 20000L, brand.id)
            val stock1 = createAndSaveStock(100L, product1.id)
            createAndSaveStock(10L, product2.id)
            val order = createAndSaveOrder(5000000L, userId)

            val orderDetails = listOf(
                OrderDetail.create(10L, brand, product1, order),
                OrderDetail.create(20L, brand, product2, order),
            )

            // when
            try {
                productService.deductAllStock(orderDetails)
                stockJpaRepository.flush()
            } catch (e: CoreException) {
                // 예외 무시
            }

            val stock1InDb = stockJpaRepository.findById(stock1.id).get()
            assertThat(stock1InDb.quantity).isEqualTo(100L)
        }
    }

    private fun createAndSaveBrand(name: String): Brand {
        return brandJpaRepository.save(Brand.create(name))
    }

    private fun createAndSaveProduct(name: String, price: Long, brandId: Long): Product {
        return productJpaRepository.save(Product.create(name, price, brandId))
    }

    private fun createAndSaveOrder(totalAmunt: Long, userId: Long): Order {
        return orderJpaRepository.save(Order.create(totalAmunt, userId))
    }

    private fun createAndSaveStock(quantity: Long, productId: Long): Stock {
        return stockJpaRepository.save(Stock.create(quantity, productId))
    }
}
