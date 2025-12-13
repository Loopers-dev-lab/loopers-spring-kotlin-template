package com.loopers.interfaces.event.product

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.order.OrderItemSnapshot
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

/**
 * ProductEventListener E2E 통합 테스트
 *
 * 검증 범위:
 * - OrderCreatedEventV1 → 재고 차감 (BEFORE_COMMIT, 동기)
 * - OrderCanceledEventV1 → 재고 복구 (BEFORE_COMMIT, 동기)
 * - LikeCreatedEventV1 → 좋아요 카운트 증가 (AFTER_COMMIT, 비동기)
 * - LikeCanceledEventV1 → 좋아요 카운트 감소 (AFTER_COMMIT, 비동기)
 */
@SpringBootTest
@DisplayName("ProductEventListener 통합 테스트")
class ProductEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("onOrderCreated")
    inner class OnOrderCreated {

        @Test
        @DisplayName("OrderCreatedEventV1 발행 시 재고가 차감된다")
        fun `OrderCreatedEventV1 triggers stock decrease`() {
            // given
            val initialStock = 100
            val orderQuantity = 5
            val product = createProduct(stockQuantity = initialStock)

            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderItemSnapshot(
                        productId = product.id,
                        quantity = orderQuantity,
                    ),
                ),
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 트랜잭션 커밋 후 재고 확인
            val stock = stockRepository.findByProductId(product.id)!!
            assertThat(stock.quantity).isEqualTo(initialStock - orderQuantity)
        }

        @Test
        @DisplayName("여러 상품이 포함된 OrderCreatedEventV1 발행 시 각 상품의 재고가 차감된다")
        fun `OrderCreatedEventV1 with multiple items triggers stock decrease for each product`() {
            // given
            val initialStock1 = 100
            val initialStock2 = 50
            val orderQuantity1 = 3
            val orderQuantity2 = 7
            val product1 = createProduct(stockQuantity = initialStock1)
            val product2 = createProduct(stockQuantity = initialStock2)

            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderItemSnapshot(productId = product1.id, quantity = orderQuantity1),
                    OrderItemSnapshot(productId = product2.id, quantity = orderQuantity2),
                ),
            )

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val stock1 = stockRepository.findByProductId(product1.id)!!
            val stock2 = stockRepository.findByProductId(product2.id)!!
            assertThat(stock1.quantity).isEqualTo(initialStock1 - orderQuantity1)
            assertThat(stock2.quantity).isEqualTo(initialStock2 - orderQuantity2)
        }
    }

    @Nested
    @DisplayName("onOrderCanceled")
    inner class OnOrderCanceled {

        @Test
        @DisplayName("OrderCanceledEventV1 발행 시 재고가 복구된다")
        fun `OrderCanceledEventV1 triggers stock increase`() {
            // given
            val initialStock = 95
            val cancelQuantity = 5
            val product = createProduct(stockQuantity = initialStock)

            val event = OrderCanceledEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderItemSnapshot(
                        productId = product.id,
                        quantity = cancelQuantity,
                    ),
                ),
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 트랜잭션 커밋 후 재고 확인
            val stock = stockRepository.findByProductId(product.id)!!
            assertThat(stock.quantity).isEqualTo(initialStock + cancelQuantity)
        }

        @Test
        @DisplayName("여러 상품이 포함된 OrderCanceledEventV1 발행 시 각 상품의 재고가 복구된다")
        fun `OrderCanceledEventV1 with multiple items triggers stock increase for each product`() {
            // given
            val initialStock1 = 97
            val initialStock2 = 43
            val cancelQuantity1 = 3
            val cancelQuantity2 = 7
            val product1 = createProduct(stockQuantity = initialStock1)
            val product2 = createProduct(stockQuantity = initialStock2)

            val event = OrderCanceledEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderItemSnapshot(productId = product1.id, quantity = cancelQuantity1),
                    OrderItemSnapshot(productId = product2.id, quantity = cancelQuantity2),
                ),
            )

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val stock1 = stockRepository.findByProductId(product1.id)!!
            val stock2 = stockRepository.findByProductId(product2.id)!!
            assertThat(stock1.quantity).isEqualTo(initialStock1 + cancelQuantity1)
            assertThat(stock2.quantity).isEqualTo(initialStock2 + cancelQuantity2)
        }
    }

    @Nested
    @DisplayName("onLikeCreated")
    inner class OnLikeCreated {

        @Test
        @DisplayName("LikeCreatedEventV1 발행 시 좋아요 카운트가 증가한다")
        fun `LikeCreatedEventV1 triggers likeCount increase`() {
            // given
            val initialLikeCount = 10L
            val product = createProduct(likeCount = initialLikeCount)

            val event = LikeCreatedEventV1(
                userId = 1L,
                productId = product.id,
            )

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val statistic = productStatisticRepository.findByProductId(product.id)!!
                assertThat(statistic.likeCount).isEqualTo(initialLikeCount + 1)
            }
        }
    }

    @Nested
    @DisplayName("onLikeCanceled")
    inner class OnLikeCanceled {

        @Test
        @DisplayName("LikeCanceledEventV1 발행 시 좋아요 카운트가 감소한다")
        fun `LikeCanceledEventV1 triggers likeCount decrease`() {
            // given
            val initialLikeCount = 10L
            val product = createProduct(likeCount = initialLikeCount)

            val event = LikeCanceledEventV1(
                userId = 1L,
                productId = product.id,
            )

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val statistic = productStatisticRepository.findByProductId(product.id)!!
                assertThat(statistic.likeCount).isEqualTo(initialLikeCount - 1)
            }
        }
    }

    // ===========================================
    // 도메인 픽스처 헬퍼
    // ===========================================

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
        likeCount: Long = 0L,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(name = "테스트 상품", price = price, brand = brand)
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.of(savedProduct.id, likeCount))
        return savedProduct
    }
}
