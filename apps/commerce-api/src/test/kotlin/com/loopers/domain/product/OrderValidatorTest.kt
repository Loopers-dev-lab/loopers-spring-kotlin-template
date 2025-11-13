package com.loopers.domain.product

import com.loopers.domain.order.OrderCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.fixtures.ProductFixtures.createProduct
import com.loopers.support.fixtures.ProductFixtures.createStock
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("OrderValidator 테스트")
class OrderValidatorTest {

    @Nested
    @DisplayName("validate 메서드는")
    inner class ValidateTest {

        @Test
        fun `모든 조건이 충족되면 검증을 통과한다`() {
            // given
            val products = listOf(
                createProduct(id = 1L, price = 10000L),
                createProduct(id = 2L, price = 20000L),
            )
            val stocks = listOf(
                createStock(productId = 1L, quantity = 100L),
                createStock(productId = 2L, quantity = 50L),
            )
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = 2L, quantity = 5),
            )

            // when & then
            assertThatCode {
                OrderValidator.validate(items, products, stocks)
            }.doesNotThrowAnyException()
        }

        @Test
        fun `재고가 정확히 주문 수량과 같을 때 검증을 통과한다`() {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L))
            val stocks = listOf(createStock(productId = 1L, quantity = 10L))
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10),
            )

            // when & then
            assertThatCode {
                OrderValidator.validate(items, products, stocks)
            }.doesNotThrowAnyException()
        }

        @Test
        fun `동일 상품에 대한 중복 주문 항목 수량 합이 재고와 같으면 검증을 통과한다`() {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L))
            val stocks = listOf(createStock(productId = 1L, quantity = 10L))
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 4),
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 3),
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 3),
            )

            // when & then
            assertThatCode {
                OrderValidator.validate(items, products, stocks)
            }.doesNotThrowAnyException()
        }
    }

    @Nested
    @DisplayName("상품 존재 여부 검증 시")
    inner class ProductExistenceValidationTest {

        @Test
        fun `존재하지 않는 상품이 하나라도 있으면 예외가 발생한다`() {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L))
            val stocks = listOf(createStock(productId = 1L, quantity = 100L))
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = 999L, quantity = 5),
            )

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("상품을 찾을 수 없습니다")
                .hasMessageContaining("999")
        }

        @Test
        fun `여러 상품이 존재하지 않으면 모두 포함하여 예외가 발생한다`() {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L))
            val stocks = listOf(createStock(productId = 1L, quantity = 100L))
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = 998L, quantity = 5),
                OrderCommand.OrderDetailCommand(productId = 999L, quantity = 3),
            )

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("998")
                .hasMessageContaining("999")
        }

        @Test
        fun `요청한 모든 상품이 존재하지 않으면 예외가 발생한다`() {
            // given
            val products = emptyList<Product>()
            val stocks = emptyList<Stock>()
            val items = listOf(OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10))

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("재고 가용성 검증 시")
    inner class StockAvailabilityValidationTest {

        @Test
        fun `재고가 부족하면 예외가 발생한다`() {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L, name = "테스트 상품"))
            val stocks = listOf(createStock(productId = 1L, quantity = 5L))
            val items = listOf(OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10))

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
                .hasMessageContaining("테스트 상품")
                .hasMessageContaining("재고가 부족합니다")
        }

        @Test
        fun `재고 정보가 없으면 예외가 발생한다`() {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L))
            val stocks = emptyList<Stock>()
            val items = listOf(OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10))

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("재고 정보를 찾을 수 없습니다")
        }

        @ParameterizedTest(name = "재고 {0}개 중 {1}개 주문 시 재고 부족 예외 발생")
        @MethodSource("com.loopers.domain.product.OrderValidatorTest#insufficientStockProvider")
        fun `재고보다 많은 수량을 주문하면 예외가 발생한다`(stockQuantity: Long, orderQuantity: Long) {
            // given
            val products = listOf(createProduct(id = 1L, price = 10000L))
            val stocks = listOf(createStock(productId = 1L, quantity = stockQuantity))
            val items = listOf(OrderCommand.OrderDetailCommand(productId = 1L, quantity = orderQuantity))

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
        }

        @Test
        fun `여러 상품 중 일부만 재고가 부족해도 예외가 발생한다`() {
            // given
            val products = listOf(
                createProduct(id = 1L, price = 10000L, name = "충분한 재고"),
                createProduct(id = 2L, price = 20000L, name = "부족한 재고"),
            )
            val stocks = listOf(
                createStock(productId = 1L, quantity = 100L),
                createStock(productId = 2L, quantity = 5L),
            )
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 10),
                OrderCommand.OrderDetailCommand(productId = 2L, quantity = 10),
            )

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
                .hasMessageContaining("부족한 재고")
        }

        @Test
        fun `여러 상품의 중복 항목을 각각 합산하여 검증한다`() {
            // given
            val products = listOf(
                createProduct(id = 1L, price = 10000L, name = "상품1"),
                createProduct(id = 2L, price = 20000L, name = "상품2"),
            )
            val stocks = listOf(
                createStock(productId = 1L, quantity = 10L),
                createStock(productId = 2L, quantity = 5L),
            )
            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 5),
                OrderCommand.OrderDetailCommand(productId = 2L, quantity = 3),
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 5),
                OrderCommand.OrderDetailCommand(productId = 2L, quantity = 3),
            )

            // when & then
            assertThatThrownBy {
                OrderValidator.validate(items, products, stocks)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
                .hasMessageContaining("상품2")
                .hasMessageContaining("요청: 6")
                .hasMessageContaining("현재: 5")
        }
    }

    companion object {
        @JvmStatic
        fun insufficientStockProvider(): Stream<Arguments> = Stream.of(
            Arguments.of(5L, 10),
            Arguments.of(0L, 1),
            Arguments.of(99L, 100),
            Arguments.of(1L, 1000),
        )
    }
}
