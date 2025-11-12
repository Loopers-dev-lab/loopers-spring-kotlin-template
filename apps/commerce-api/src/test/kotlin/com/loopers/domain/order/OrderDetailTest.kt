package com.loopers.domain.order

import com.loopers.support.fixtures.BrandFixtures.createBrand
import com.loopers.support.fixtures.OrderFixtures.createOrder
import com.loopers.support.fixtures.ProductFixtures.createProduct
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
                softly.assertThat(orderDetail.brandId).isEqualTo(1L)
                softly.assertThat(orderDetail.productId).isEqualTo(product.id)
                softly.assertThat(orderDetail.orderId).isEqualTo(order.id)
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
}
