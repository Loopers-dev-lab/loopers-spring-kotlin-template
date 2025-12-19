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

    @DisplayName("findAllByProductIds()")
    @Nested
    inner class FindAllByProductIds {

        @DisplayName("일치하는 productId 목록에 해당하는 ProductStatistic들을 반환한다")
        @Test
        fun `returns matching statistics for given productIds`() {
            // given
            val productStatistic1 = ProductStatistic(productId = 1L, likeCount = 10, salesCount = 5, viewCount = 100)
            val productStatistic2 = ProductStatistic(productId = 2L, likeCount = 20, salesCount = 10, viewCount = 200)
            val productStatistic3 = ProductStatistic(productId = 3L, likeCount = 30, salesCount = 15, viewCount = 300)
            productStatisticJpaRepository.saveAllAndFlush(listOf(productStatistic1, productStatistic2, productStatistic3))

            // when
            val result = productStatisticRepository.findAllByProductIds(listOf(1L, 2L))

            // then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("빈 목록을 전달하면 빈 결과를 반환한다")
        @Test
        fun `returns empty list when given empty productIds`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10, salesCount = 5, viewCount = 100)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            val result = productStatisticRepository.findAllByProductIds(emptyList())

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("존재하지 않는 productId는 결과에 포함되지 않는다")
        @Test
        fun `returns only existing statistics when some productIds do not exist`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10, salesCount = 5, viewCount = 100)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            val result = productStatisticRepository.findAllByProductIds(listOf(1L, 999L))

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(1L)
        }
    }

    @DisplayName("saveAll()")
    @Nested
    inner class SaveAll {

        @DisplayName("여러 ProductStatistic을 한 번에 저장한다")
        @Test
        fun `persists multiple statistics at once`() {
            // given
            val productStatistic1 = ProductStatistic(productId = 1L, likeCount = 10, salesCount = 5, viewCount = 100)
            val productStatistic2 = ProductStatistic(productId = 2L, likeCount = 20, salesCount = 10, viewCount = 200)

            // when
            val result = productStatisticRepository.saveAll(listOf(productStatistic1, productStatistic2))

            // then
            assertThat(result).hasSize(2)
            val savedStatistics = productStatisticJpaRepository.findAll()
            assertThat(savedStatistics).hasSize(2)
            assertThat(savedStatistics.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("빈 목록을 전달하면 빈 결과를 반환한다")
        @Test
        fun `returns empty list when given empty statistics list`() {
            // when
            val result = productStatisticRepository.saveAll(emptyList())

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("기존 엔티티를 업데이트할 수 있다")
        @Test
        fun `updates existing entities`() {
            // given
            val productStatistic = ProductStatistic(productId = 1L, likeCount = 10, salesCount = 5, viewCount = 100)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            // when
            productStatistic.likeCount = 20
            productStatistic.salesCount = 15
            productStatisticRepository.saveAll(listOf(productStatistic))

            // then
            val result = productStatisticJpaRepository.findByProductId(1L)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(20)
            assertThat(result.salesCount).isEqualTo(15)
        }
    }
}
