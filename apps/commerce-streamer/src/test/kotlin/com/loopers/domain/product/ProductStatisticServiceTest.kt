package com.loopers.domain.product

import io.mockk.every
import io.mockk.mockk
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

    @DisplayName("updateLikeCount()")
    @Nested
    inner class UpdateLikeCount {

        @DisplayName("빈 command인 경우 repository를 호출하지 않고 즉시 반환한다")
        @Test
        fun `returns early when command items is empty`() {
            // given
            val command = UpdateLikeCountCommand(items = emptyList())

            // when
            productStatisticService.updateLikeCount(command)

            // then
            verify(exactly = 0) { productStatisticRepository.findAllByProductIds(any()) }
            verify(exactly = 0) { productStatisticRepository.saveAll(any()) }
        }

        @DisplayName("모든 productId에 대해 통계를 조회하고 변경사항을 적용하여 저장한다")
        @Test
        fun `fetches all productIds, applies changes, and saves`() {
            // given
            val statistic1 = ProductStatistic(id = 1, productId = 1L, likeCount = 10)
            val statistic2 = ProductStatistic(id = 2, productId = 2L, likeCount = 5)
            val command = UpdateLikeCountCommand(
                items = listOf(
                    UpdateLikeCountCommand.Item(productId = 1L, type = UpdateLikeCountCommand.LikeType.CREATED),
                    UpdateLikeCountCommand.Item(productId = 2L, type = UpdateLikeCountCommand.LikeType.CANCELED),
                    UpdateLikeCountCommand.Item(productId = 1L, type = UpdateLikeCountCommand.LikeType.CREATED),
                ),
            )

            every { productStatisticRepository.findAllByProductIds(listOf(1L, 2L)) } returns listOf(statistic1, statistic2)
            every { productStatisticRepository.saveAll(any()) } answers { firstArg() }

            // when
            productStatisticService.updateLikeCount(command)

            // then
            verify(exactly = 1) { productStatisticRepository.findAllByProductIds(listOf(1L, 2L)) }
            verify(exactly = 1) {
                productStatisticRepository.saveAll(
                    match { statistics ->
                    statistics.size == 2 &&
                        statistics.find { it.productId == 1L }?.likeCount == 12L && // 10 + 2
                        statistics.find { it.productId == 2L }?.likeCount == 4L // 5 - 1
                },
                )
            }
        }

        @DisplayName("존재하지 않는 productId는 건너뛴다")
        @Test
        fun `skips missing statistics`() {
            // given
            val statistic1 = ProductStatistic(id = 1, productId = 1L, likeCount = 10)
            // productId 999L은 존재하지 않음
            val command = UpdateLikeCountCommand(
                items = listOf(
                    UpdateLikeCountCommand.Item(productId = 1L, type = UpdateLikeCountCommand.LikeType.CREATED),
                    UpdateLikeCountCommand.Item(productId = 999L, type = UpdateLikeCountCommand.LikeType.CREATED),
                ),
            )

            every { productStatisticRepository.findAllByProductIds(listOf(1L, 999L)) } returns listOf(statistic1)
            every { productStatisticRepository.saveAll(any()) } answers { firstArg() }

            // when
            productStatisticService.updateLikeCount(command)

            // then
            verify(exactly = 1) {
                productStatisticRepository.saveAll(
                    match { statistics ->
                    statistics.size == 1 && statistics[0].productId == 1L
                },
                )
            }
        }
    }

    @DisplayName("updateSalesCount()")
    @Nested
    inner class UpdateSalesCount {

        @DisplayName("빈 command인 경우 repository를 호출하지 않고 즉시 반환한다")
        @Test
        fun `returns early when command items is empty`() {
            // given
            val command = UpdateSalesCountCommand(items = emptyList())

            // when
            productStatisticService.updateSalesCount(command)

            // then
            verify(exactly = 0) { productStatisticRepository.findAllByProductIds(any()) }
            verify(exactly = 0) { productStatisticRepository.saveAll(any()) }
        }

        @DisplayName("여러 아이템을 productId별로 집계하여 저장한다")
        @Test
        fun `aggregates multiple items correctly`() {
            // given
            val statistic1 = ProductStatistic(id = 1, productId = 1L, salesCount = 100)
            val statistic2 = ProductStatistic(id = 2, productId = 2L, salesCount = 50)
            val command = UpdateSalesCountCommand(
                items = listOf(
                    UpdateSalesCountCommand.Item(productId = 1L, quantity = 3),
                    UpdateSalesCountCommand.Item(productId = 2L, quantity = 2),
                    UpdateSalesCountCommand.Item(productId = 1L, quantity = 5),
                ),
            )

            every { productStatisticRepository.findAllByProductIds(listOf(1L, 2L)) } returns listOf(statistic1, statistic2)
            every { productStatisticRepository.saveAll(any()) } answers { firstArg() }

            // when
            productStatisticService.updateSalesCount(command)

            // then
            verify(exactly = 1) { productStatisticRepository.findAllByProductIds(listOf(1L, 2L)) }
            verify(exactly = 1) {
                productStatisticRepository.saveAll(
                    match { statistics ->
                    statistics.size == 2 &&
                        statistics.find { it.productId == 1L }?.salesCount == 108L && // 100 + 3 + 5
                        statistics.find { it.productId == 2L }?.salesCount == 52L // 50 + 2
                },
                )
            }
        }

        @DisplayName("존재하지 않는 productId는 건너뛴다")
        @Test
        fun `skips missing statistics`() {
            // given
            val statistic1 = ProductStatistic(id = 1, productId = 1L, salesCount = 100)
            val command = UpdateSalesCountCommand(
                items = listOf(
                    UpdateSalesCountCommand.Item(productId = 1L, quantity = 5),
                    UpdateSalesCountCommand.Item(productId = 999L, quantity = 10),
                ),
            )

            every { productStatisticRepository.findAllByProductIds(listOf(1L, 999L)) } returns listOf(statistic1)
            every { productStatisticRepository.saveAll(any()) } answers { firstArg() }

            // when
            productStatisticService.updateSalesCount(command)

            // then
            verify(exactly = 1) {
                productStatisticRepository.saveAll(
                    match { statistics ->
                    statistics.size == 1 && statistics[0].productId == 1L
                },
                )
            }
        }
    }

    @DisplayName("updateViewCount()")
    @Nested
    inner class UpdateViewCount {

        @DisplayName("빈 command인 경우 repository를 호출하지 않고 즉시 반환한다")
        @Test
        fun `returns early when command items is empty`() {
            // given
            val command = UpdateViewCountCommand(items = emptyList())

            // when
            productStatisticService.updateViewCount(command)

            // then
            verify(exactly = 0) { productStatisticRepository.findAllByProductIds(any()) }
            verify(exactly = 0) { productStatisticRepository.saveAll(any()) }
        }

        @DisplayName("productId별로 조회 횟수를 집계하여 저장한다")
        @Test
        fun `counts by productId correctly`() {
            // given
            val statistic1 = ProductStatistic(id = 1, productId = 1L, viewCount = 1000)
            val statistic2 = ProductStatistic(id = 2, productId = 2L, viewCount = 500)
            val command = UpdateViewCountCommand(
                items = listOf(
                    UpdateViewCountCommand.Item(productId = 1L),
                    UpdateViewCountCommand.Item(productId = 1L),
                    UpdateViewCountCommand.Item(productId = 1L),
                    UpdateViewCountCommand.Item(productId = 2L),
                ),
            )

            every { productStatisticRepository.findAllByProductIds(listOf(1L, 2L)) } returns listOf(statistic1, statistic2)
            every { productStatisticRepository.saveAll(any()) } answers { firstArg() }

            // when
            productStatisticService.updateViewCount(command)

            // then
            verify(exactly = 1) { productStatisticRepository.findAllByProductIds(listOf(1L, 2L)) }
            verify(exactly = 1) {
                productStatisticRepository.saveAll(
                    match { statistics ->
                    statistics.size == 2 &&
                        statistics.find { it.productId == 1L }?.viewCount == 1003L && // 1000 + 3
                        statistics.find { it.productId == 2L }?.viewCount == 501L // 500 + 1
                },
                )
            }
        }

        @DisplayName("존재하지 않는 productId는 건너뛴다")
        @Test
        fun `skips missing statistics`() {
            // given
            val statistic1 = ProductStatistic(id = 1, productId = 1L, viewCount = 1000)
            val command = UpdateViewCountCommand(
                items = listOf(
                    UpdateViewCountCommand.Item(productId = 1L),
                    UpdateViewCountCommand.Item(productId = 999L),
                ),
            )

            every { productStatisticRepository.findAllByProductIds(listOf(1L, 999L)) } returns listOf(statistic1)
            every { productStatisticRepository.saveAll(any()) } answers { firstArg() }

            // when
            productStatisticService.updateViewCount(command)

            // then
            verify(exactly = 1) {
                productStatisticRepository.saveAll(
                    match { statistics ->
                    statistics.size == 1 && statistics[0].productId == 1L
                },
                )
            }
        }
    }
}
