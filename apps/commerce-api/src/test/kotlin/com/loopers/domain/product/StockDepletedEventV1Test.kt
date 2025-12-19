package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

@DisplayName("StockDepletedEventV1 테스트")
class StockDepletedEventV1Test {

    @DisplayName("from 팩토리 메서드 테스트")
    @Nested
    inner class From {

        @DisplayName("Stock에서 올바른 속성으로 이벤트가 생성된다")
        @Test
        fun `from() factory creates event with correct properties`() {
            // given
            val stock = Stock.create(
                productId = 100L,
                quantity = 0,
            )

            // when
            val event = StockDepletedEventV1.from(stock)

            // then
            assertThat(event.productId).isEqualTo(100L)
            assertThat(event.stockId).isEqualTo(stock.id)
            assertThat(event.occurredAt).isNotNull()
        }
    }
}
