package com.loopers.domain.product

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProductStatisticService 단위 테스트")
class ProductStatisticServiceTest {

    private lateinit var productStatisticRepository: ProductStatisticRepository
    private lateinit var productStatisticService: ProductStatisticService

    @BeforeEach
    fun setUp() {
        productStatisticRepository = mockk()
        productStatisticService = ProductStatisticService(productStatisticRepository)
    }

    @DisplayName("increaseLikeCount()")
    @Nested
    inner class IncreaseLikeCount {

        @DisplayName("productId로 repository.incrementLikeCount()를 호출한다")
        @Test
        fun `calls repository incrementLikeCount with productId`() {
            // given
            val productId = 1L
            every { productStatisticRepository.incrementLikeCount(productId) } just runs

            // when
            productStatisticService.increaseLikeCount(productId)

            // then
            verify(exactly = 1) { productStatisticRepository.incrementLikeCount(productId) }
        }
    }

    @DisplayName("decreaseLikeCount()")
    @Nested
    inner class DecreaseLikeCount {

        @DisplayName("productId로 repository.decrementLikeCount()를 호출한다")
        @Test
        fun `calls repository decrementLikeCount with productId`() {
            // given
            val productId = 1L
            every { productStatisticRepository.decrementLikeCount(productId) } just runs

            // when
            productStatisticService.decreaseLikeCount(productId)

            // then
            verify(exactly = 1) { productStatisticRepository.decrementLikeCount(productId) }
        }
    }

    @DisplayName("increaseSalesCount()")
    @Nested
    inner class IncreaseSalesCount {

        @DisplayName("각 orderItem에 대해 repository.incrementSalesCount()를 호출한다")
        @Test
        fun `calls repository incrementSalesCount for each orderItem`() {
            // given
            val orderItems = listOf(
                OrderItemSnapshot(productId = 1L, quantity = 2),
                OrderItemSnapshot(productId = 2L, quantity = 3),
                OrderItemSnapshot(productId = 3L, quantity = 1),
            )
            every { productStatisticRepository.incrementSalesCount(any(), any()) } just runs

            // when
            productStatisticService.increaseSalesCount(orderItems)

            // then
            verify(exactly = 1) { productStatisticRepository.incrementSalesCount(1L, 2) }
            verify(exactly = 1) { productStatisticRepository.incrementSalesCount(2L, 3) }
            verify(exactly = 1) { productStatisticRepository.incrementSalesCount(3L, 1) }
        }

        @DisplayName("빈 리스트인 경우 repository를 호출하지 않는다")
        @Test
        fun `does not call repository when orderItems is empty`() {
            // given
            val orderItems = emptyList<OrderItemSnapshot>()

            // when
            productStatisticService.increaseSalesCount(orderItems)

            // then
            verify(exactly = 0) { productStatisticRepository.incrementSalesCount(any(), any()) }
        }

        @DisplayName("단일 orderItem에 대해 repository.incrementSalesCount()를 호출한다")
        @Test
        fun `calls repository incrementSalesCount for single orderItem`() {
            // given
            val orderItems = listOf(
                OrderItemSnapshot(productId = 100L, quantity = 5),
            )
            every { productStatisticRepository.incrementSalesCount(any(), any()) } just runs

            // when
            productStatisticService.increaseSalesCount(orderItems)

            // then
            verify(exactly = 1) { productStatisticRepository.incrementSalesCount(100L, 5) }
        }
    }

    @DisplayName("increaseViewCount()")
    @Nested
    inner class IncreaseViewCount {

        @DisplayName("productId로 repository.incrementViewCount()를 호출한다")
        @Test
        fun `calls repository incrementViewCount with productId`() {
            // given
            val productId = 1L
            every { productStatisticRepository.incrementViewCount(productId) } just runs

            // when
            productStatisticService.increaseViewCount(productId)

            // then
            verify(exactly = 1) { productStatisticRepository.incrementViewCount(productId) }
        }
    }
}
