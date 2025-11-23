package com.loopers.domain.order

import com.loopers.support.fixtures.BrandFixtures.createBrand
import com.loopers.support.fixtures.OrderFixtures.createOrder
import com.loopers.support.fixtures.ProductFixtures.createProduct
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("OrderDetail 도메인 테스트")
class OrderDetailTest {

    @Nested
    @DisplayName("주문 상세 생성")
    inner class CreateTest {

        @ParameterizedTest
        @ValueSource(longs = [1L, 10L, 999L])
        @DisplayName("유효한 수량일 때 정상적으로 OrderDetail이 생성된다")
        fun createOrderDetail(quantity: Long) {
            // given
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = createOrder()

            // when
            val orderDetail = OrderDetail.create(quantity, brand, product, order)

            // then
            assertSoftly { softly ->
                softly.assertThat(orderDetail.brandName).isEqualTo(brand.name)
                softly.assertThat(orderDetail.productName).isEqualTo(product.name)
                softly.assertThat(orderDetail.price).isEqualTo(product.price)
                softly.assertThat(orderDetail.quantity).isEqualTo(quantity)
            }
        }

        @ParameterizedTest
        @ValueSource(longs = [0L, -1L, -10L])
        @DisplayName("주문 수량이 0 이하일 경우 예외가 발생한다")
        fun createOrderDetailWithInvalidQuantity(quantity: Long) {
            // given
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = createOrder()

            // when & then
            assertThatThrownBy {
                OrderDetail.create(quantity, brand, product, order)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("주문 수량은 0보다 커야 합니다.")
        }

        @ParameterizedTest
        @ValueSource(longs = [1000L, 1001L, 2000L])
        @DisplayName("주문 수량이 999를 초과할 경우 예외가 발생한다")
        fun createOrderDetailWithTooLargeQuantity(quantity: Long) {
            // given
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            val order = createOrder()

            // when & then
            assertThatThrownBy {
                OrderDetail.create(quantity, brand, product, order)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("주문 수량은 999개를 초과할 수 없습니다.")
        }
    }

    @Nested
    @DisplayName("여러 주문 상세 생성")
    inner class CreateListTest {

        @Test
        @DisplayName("여러 상품 주문 시 모든 상품에 대한 OrderDetail이 생성된다")
        fun createMultipleOrderDetails() {
            // given
            val brand1 = createBrand(id = 1L, name = "브랜드A")
            val brand2 = createBrand(id = 2L, name = "브랜드B")
            val product1 = createProduct(id = 1L, brandId = 1L, name = "상품1")
            val product2 = createProduct(id = 2L, brandId = 2L, name = "상품2")
            val product3 = createProduct(id = 3L, brandId = 1L, name = "상품3")
            val order = createOrder(id = 1L)

            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 2L),
                OrderCommand.OrderDetailCommand(productId = 2L, quantity = 1L),
                OrderCommand.OrderDetailCommand(productId = 3L, quantity = 5L),
            )

            // when
            val orderDetails = OrderDetail.create(
                items = items,
                brands = listOf(brand1, brand2),
                products = listOf(product1, product2, product3),
                order = order,
            )

            // then
            assertSoftly { softly ->
                softly.assertThat(orderDetails).hasSize(3)

                // 첫 번째 주문 상세
                softly.assertThat(orderDetails[0].productId).isEqualTo(1L)
                softly.assertThat(orderDetails[0].brandId).isEqualTo(1L)
                softly.assertThat(orderDetails[0].quantity).isEqualTo(2L)
                softly.assertThat(orderDetails[0].productName).isEqualTo("상품1")
                softly.assertThat(orderDetails[0].brandName).isEqualTo("브랜드A")

                // 두 번째 주문 상세
                softly.assertThat(orderDetails[1].productId).isEqualTo(2L)
                softly.assertThat(orderDetails[1].brandId).isEqualTo(2L)
                softly.assertThat(orderDetails[1].quantity).isEqualTo(1L)
                softly.assertThat(orderDetails[1].productName).isEqualTo("상품2")
                softly.assertThat(orderDetails[1].brandName).isEqualTo("브랜드B")

                // 세 번째 주문 상세
                softly.assertThat(orderDetails[2].productId).isEqualTo(3L)
                softly.assertThat(orderDetails[2].brandId).isEqualTo(1L)
                softly.assertThat(orderDetails[2].quantity).isEqualTo(5L)
                softly.assertThat(orderDetails[2].productName).isEqualTo("상품3")
                softly.assertThat(orderDetails[2].brandName).isEqualTo("브랜드A")

                // 모든 주문 상세가 동일한 orderId를 가지는지
                softly.assertThat(orderDetails.map { it.orderId }.distinct()).hasSize(1)
            }
        }

        @Test
        @DisplayName("같은 브랜드의 여러 상품 주문 시 정상적으로 생성된다")
        fun createOrderDetailsWithSameBrand() {
            // given
            val brand = createBrand(id = 1L)
            val product1 = createProduct(id = 1L, brandId = 1L)
            val product2 = createProduct(id = 2L, brandId = 1L)
            val order = createOrder(id = 1L)

            val items = listOf(
                OrderCommand.OrderDetailCommand(productId = 1L, quantity = 3L),
                OrderCommand.OrderDetailCommand(productId = 2L, quantity = 4L),
            )

            // when
            val orderDetails = OrderDetail.create(
                items = items,
                brands = listOf(brand),
                products = listOf(product1, product2),
                order = order,
            )

            // then
            assertSoftly { softly ->
                softly.assertThat(orderDetails).hasSize(2)
                softly.assertThat(orderDetails.map { it.brandId }.distinct()).containsOnly(1L)
                softly.assertThat(orderDetails[0].quantity).isEqualTo(3L)
                softly.assertThat(orderDetails[1].quantity).isEqualTo(4L)
            }
        }

        @Test
        @DisplayName("빈 주문 항목일 때 예외가 발생한다")
        fun createEmptyOrderDetails() {
            // given
            val order = createOrder(id = 1L)
            val items = emptyList<OrderCommand.OrderDetailCommand>()

            // when & then
            assertThatThrownBy {
                OrderDetail.create(
                    items = items,
                    brands = emptyList(),
                    products = emptyList(),
                    order = order,
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("주문 항목은 최소 1개 이상이어야 합니다.")
        }
    }
}
