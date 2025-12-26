package com.loopers.domain.product

import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("ProductStatisticService 통합 테스트")
class ProductStatisticServiceIntegrationTest @Autowired constructor(
    private val productStatisticService: ProductStatisticService,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("updateLikeCount 테스트")
    @Nested
    inner class UpdateLikeCount {

        @Test
        @DisplayName("빈 커맨드 시 아무 작업도 하지 않는다")
        fun `does nothing when command is empty`() {
            // given
            val command = UpdateLikeCountCommand(items = emptyList())

            // when
            productStatisticService.updateLikeCount(command)

            // then
            val result = productStatisticJpaRepository.findAll()
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("단일 상품의 좋아요 수가 증가한다")
        fun `increases like count for single product`() {
            // given
            val productId = 1L
            val initialLikeCount = 10L
            saveProductStatistic(productId = productId, likeCount = initialLikeCount)

            val command = UpdateLikeCountCommand(
                items = listOf(
                    UpdateLikeCountCommand.Item(productId = productId, type = UpdateLikeCountCommand.LikeType.CREATED),
                ),
            )

            // when
            productStatisticService.updateLikeCount(command)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount + 1)
        }

        @Test
        @DisplayName("좋아요 취소 시 좋아요 수가 감소한다")
        fun `decreases like count when like is canceled`() {
            // given
            val productId = 1L
            val initialLikeCount = 10L
            saveProductStatistic(productId = productId, likeCount = initialLikeCount)

            val command = UpdateLikeCountCommand(
                items = listOf(
                    UpdateLikeCountCommand.Item(productId = productId, type = UpdateLikeCountCommand.LikeType.CANCELED),
                ),
            )

            // when
            productStatisticService.updateLikeCount(command)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount - 1)
        }

        @Test
        @DisplayName("여러 상품의 좋아요 수가 배치로 증가한다")
        fun `increases like count for multiple products in batch`() {
            // given
            val productId1 = 1L
            val productId2 = 2L
            saveProductStatistic(productId = productId1, likeCount = 10L)
            saveProductStatistic(productId = productId2, likeCount = 20L)

            val command = UpdateLikeCountCommand(
                items = listOf(
                    UpdateLikeCountCommand.Item(productId = productId1, type = UpdateLikeCountCommand.LikeType.CREATED),
                    UpdateLikeCountCommand.Item(productId = productId2, type = UpdateLikeCountCommand.LikeType.CREATED),
                    UpdateLikeCountCommand.Item(productId = productId1, type = UpdateLikeCountCommand.LikeType.CREATED),
                ),
            )

            // when
            productStatisticService.updateLikeCount(command)

            // then
            val result1 = productStatisticJpaRepository.findByProductId(productId1)
            val result2 = productStatisticJpaRepository.findByProductId(productId2)
            assertThat(result1!!.likeCount).isEqualTo(12L) // 10 + 2
            assertThat(result2!!.likeCount).isEqualTo(21L) // 20 + 1
        }

        @Test
        @DisplayName("존재하지 않는 상품은 무시된다")
        fun `ignores non-existing products`() {
            // given
            val existingProductId = 1L
            val nonExistingProductId = 999L
            saveProductStatistic(productId = existingProductId, likeCount = 10L)

            val command = UpdateLikeCountCommand(
                items = listOf(
                    UpdateLikeCountCommand.Item(productId = existingProductId, type = UpdateLikeCountCommand.LikeType.CREATED),
                    UpdateLikeCountCommand.Item(productId = nonExistingProductId, type = UpdateLikeCountCommand.LikeType.CREATED),
                ),
            )

            // when
            productStatisticService.updateLikeCount(command)

            // then
            val existingResult = productStatisticJpaRepository.findByProductId(existingProductId)
            val nonExistingResult = productStatisticJpaRepository.findByProductId(nonExistingProductId)
            assertThat(existingResult!!.likeCount).isEqualTo(11L)
            assertThat(nonExistingResult).isNull()
        }
    }

    @DisplayName("updateSalesCount 테스트")
    @Nested
    inner class UpdateSalesCount {

        @Test
        @DisplayName("빈 커맨드 시 아무 작업도 하지 않는다")
        fun `does nothing when command is empty`() {
            // given
            val command = UpdateSalesCountCommand(items = emptyList())

            // when
            productStatisticService.updateSalesCount(command)

            // then
            val result = productStatisticJpaRepository.findAll()
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("단일 상품의 판매 수량이 증가한다")
        fun `increases sales count for single product`() {
            // given
            val productId = 1L
            val initialSalesCount = 5L
            val quantity = 3
            saveProductStatistic(productId = productId, salesCount = initialSalesCount)

            val command = UpdateSalesCountCommand(
                items = listOf(
                    UpdateSalesCountCommand.Item(productId = productId, quantity = quantity),
                ),
            )

            // when
            productStatisticService.updateSalesCount(command)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount + quantity)
        }

        @Test
        @DisplayName("여러 상품의 판매 수량이 배치로 증가한다")
        fun `increases sales count for multiple products in batch`() {
            // given
            val productId1 = 1L
            val productId2 = 2L
            saveProductStatistic(productId = productId1, salesCount = 10L)
            saveProductStatistic(productId = productId2, salesCount = 20L)

            val command = UpdateSalesCountCommand(
                items = listOf(
                    UpdateSalesCountCommand.Item(productId = productId1, quantity = 2),
                    UpdateSalesCountCommand.Item(productId = productId2, quantity = 3),
                    UpdateSalesCountCommand.Item(productId = productId1, quantity = 1),
                ),
            )

            // when
            productStatisticService.updateSalesCount(command)

            // then
            val result1 = productStatisticJpaRepository.findByProductId(productId1)
            val result2 = productStatisticJpaRepository.findByProductId(productId2)
            assertThat(result1!!.salesCount).isEqualTo(13L) // 10 + 2 + 1
            assertThat(result2!!.salesCount).isEqualTo(23L) // 20 + 3
        }
    }

    @DisplayName("updateViewCount 테스트")
    @Nested
    inner class UpdateViewCount {

        @Test
        @DisplayName("빈 커맨드 시 아무 작업도 하지 않는다")
        fun `does nothing when command is empty`() {
            // given
            val command = UpdateViewCountCommand(items = emptyList())

            // when
            productStatisticService.updateViewCount(command)

            // then
            val result = productStatisticJpaRepository.findAll()
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("단일 상품의 조회 수가 증가한다")
        fun `increases view count for single product`() {
            // given
            val productId = 1L
            val initialViewCount = 100L
            saveProductStatistic(productId = productId, viewCount = initialViewCount)

            val command = UpdateViewCountCommand(
                items = listOf(
                    UpdateViewCountCommand.Item(productId = productId),
                ),
            )

            // when
            productStatisticService.updateViewCount(command)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(initialViewCount + 1)
        }

        @Test
        @DisplayName("동일 상품의 여러 조회가 합산된다")
        fun `sums multiple views for same product`() {
            // given
            val productId = 1L
            val initialViewCount = 100L
            saveProductStatistic(productId = productId, viewCount = initialViewCount)

            val command = UpdateViewCountCommand(
                items = listOf(
                    UpdateViewCountCommand.Item(productId = productId),
                    UpdateViewCountCommand.Item(productId = productId),
                    UpdateViewCountCommand.Item(productId = productId),
                ),
            )

            // when
            productStatisticService.updateViewCount(command)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(initialViewCount + 3)
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun saveProductStatistic(
        productId: Long = 1L,
        likeCount: Long = 0L,
        salesCount: Long = 0L,
        viewCount: Long = 0L,
    ): ProductStatistic {
        val productStatistic = ProductStatistic(
            productId = productId,
            likeCount = likeCount,
            salesCount = salesCount,
            viewCount = viewCount,
        )
        return productStatisticJpaRepository.saveAndFlush(productStatistic)
    }
}
