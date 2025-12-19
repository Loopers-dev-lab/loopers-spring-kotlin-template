package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("ProductStatisticRdbRepository 통합 테스트")
class ProductStatisticRdbRepositoryIntegrationTest @Autowired constructor(
    private val productStatisticRepository: ProductStatisticRepository,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("findByProductId()")
    @Nested
    inner class FindByProductId {

        @DisplayName("레코드가 없으면 null을 반환한다")
        @Test
        fun `returns null when no record exists for given productId`() {
            // given
            val productId = 999L

            // when
            val result = productStatisticRepository.findByProductId(productId)

            // then
            assertThat(result).isNull()
        }

        @DisplayName("레코드가 존재하면 ProductStatistic을 반환한다")
        @Test
        fun `returns ProductStatistic when record exists for given productId`() {
            // given
            val productId = 1L
            val productStatistic = ProductStatistic(
                productId = productId,
                likeCount = 10,
                salesCount = 5,
                viewCount = 100,
            )
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            val result = productStatisticRepository.findByProductId(productId)

            // then
            assertThat(result).isNotNull
            assertThat(result!!.productId).isEqualTo(productId)
            assertThat(result.likeCount).isEqualTo(10)
            assertThat(result.salesCount).isEqualTo(5)
            assertThat(result.viewCount).isEqualTo(100)
        }
    }

    @DisplayName("incrementLikeCount()")
    @Nested
    inner class IncrementLikeCount {

        @DisplayName("likeCount를 1 증가시킨다")
        @Test
        fun `increments likeCount by 1 for given productId`() {
            // given
            val productId = 1L
            val initialLikeCount = 10L
            val productStatistic = ProductStatistic(
                productId = productId,
                likeCount = initialLikeCount,
            )
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            productStatisticRepository.incrementLikeCount(productId)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount + 1)
        }
    }

    @DisplayName("decrementLikeCount()")
    @Nested
    inner class DecrementLikeCount {

        @DisplayName("likeCount를 1 감소시킨다")
        @Test
        fun `decrements likeCount by 1 for given productId`() {
            // given
            val productId = 1L
            val initialLikeCount = 10L
            val productStatistic = ProductStatistic(
                productId = productId,
                likeCount = initialLikeCount,
            )
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            productStatisticRepository.decrementLikeCount(productId)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount - 1)
        }
    }

    @DisplayName("incrementSalesCount()")
    @Nested
    inner class IncrementSalesCount {

        @DisplayName("salesCount를 지정된 양만큼 증가시킨다")
        @Test
        fun `increments salesCount by given amount for given productId`() {
            // given
            val productId = 1L
            val initialSalesCount = 5L
            val amount = 3
            val productStatistic = ProductStatistic(
                productId = productId,
                salesCount = initialSalesCount,
            )
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            productStatisticRepository.incrementSalesCount(productId, amount)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount + amount)
        }
    }

    @DisplayName("incrementViewCount()")
    @Nested
    inner class IncrementViewCount {

        @DisplayName("viewCount를 1 증가시킨다")
        @Test
        fun `increments viewCount by 1 for given productId`() {
            // given
            val productId = 1L
            val initialViewCount = 100L
            val productStatistic = ProductStatistic(
                productId = productId,
                viewCount = initialViewCount,
            )
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            productStatisticRepository.incrementViewCount(productId)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(initialViewCount + 1)
        }
    }
}
