package com.loopers.domain.ranking

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RankingService 단위 테스트")
class RankingServiceTest {

    private lateinit var rankingRepository: RankingRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var rankingService: RankingService

    @BeforeEach
    fun setUp() {
        rankingRepository = mockk()
        productRepository = mockk()
        rankingService = RankingService(rankingRepository, productRepository)
    }

    @Test
    @DisplayName("일간 Top-N 랭킹 조회 성공")
    fun `should get daily top N rankings successfully`() {
        // given
        val window = TimeWindow.DAILY
        val timestamp = "20251220"
        val page = 1
        val size = 20

        val rankings = listOf(
            Ranking(productId = 100L, score = RankingScore(10.0), rank = 1),
            Ranking(productId = 101L, score = RankingScore(8.5), rank = 2),
            Ranking(productId = 102L, score = RankingScore(7.2), rank = 3),
        )

        every { rankingRepository.getTopN(any(), 0, 19) } returns rankings
        every { rankingRepository.getCount(any()) } returns 100L

        // when
        val (result, totalCount) = rankingService.getTopN(window, timestamp, page, size)

        // then
        assertThat(result).hasSize(3)
        assertThat(result[0].productId).isEqualTo(100L)
        assertThat(result[0].rank).isEqualTo(1)
        assertThat(totalCount).isEqualTo(100L)

        verify(exactly = 1) { rankingRepository.getTopN(any(), 0, 19) }
        verify(exactly = 1) { rankingRepository.getCount(any()) }
    }

    @Test
    @DisplayName("시간별 Top-N 랭킹 조회 성공")
    fun `should get hourly top N rankings successfully`() {
        // given
        val window = TimeWindow.HOURLY
        val timestamp = "2025122014"
        val page = 1
        val size = 10

        val rankings = listOf(
            Ranking(productId = 200L, score = RankingScore(5.0), rank = 1),
        )

        every { rankingRepository.getTopN(any(), 0, 9) } returns rankings
        every { rankingRepository.getCount(any()) } returns 50L

        // when
        val (result, totalCount) = rankingService.getTopN(window, timestamp, page, size)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].productId).isEqualTo(200L)
        assertThat(totalCount).isEqualTo(50L)

        verify(exactly = 1) { rankingRepository.getTopN(any(), 0, 9) }
    }

    @Test
    @DisplayName("2페이지 랭킹 조회 - 올바른 start/end 인덱스 계산")
    fun `should calculate correct start and end index for page 2`() {
        // given
        val window = TimeWindow.DAILY
        val timestamp = "20251220"
        val page = 2
        val size = 20

        every { rankingRepository.getTopN(any(), 20, 39) } returns emptyList()
        every { rankingRepository.getCount(any()) } returns 100L

        // when
        rankingService.getTopN(window, timestamp, page, size)

        // then
        verify(exactly = 1) { rankingRepository.getTopN(any(), 20, 39) }
    }

    @Test
    @DisplayName("잘못된 페이지 번호 - 예외 발생")
    fun `should throw exception for invalid page number`() {
        assertThatThrownBy {
            rankingService.getTopN(TimeWindow.DAILY, "20251220", 0, 20)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("페이지 번호는 1 이상이어야 합니다")
    }

    @Test
    @DisplayName("잘못된 페이지 크기 - 예외 발생")
    fun `should throw exception for invalid page size`() {
        assertThatThrownBy {
            rankingService.getTopN(TimeWindow.DAILY, "20251220", 1, 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("페이지 크기는 0보다 커야 합니다")
    }

    @Test
    @DisplayName("특정 상품의 일간 랭킹 조회 성공")
    fun `should get product daily ranking successfully`() {
        // given
        val productId = 100L
        val window = TimeWindow.DAILY

        every { rankingRepository.getRank(any(), productId) } returns 5
        every { rankingRepository.getScore(any(), productId) } returns RankingScore(7.5)

        // when
        val ranking = rankingService.getProductRanking(productId, window)

        // then
        assertThat(ranking).isNotNull
        assertThat(ranking!!.productId).isEqualTo(productId)
        assertThat(ranking.rank).isEqualTo(5)
        assertThat(ranking.score.value).isEqualTo(7.5)

        verify(exactly = 1) { rankingRepository.getRank(any(), productId) }
        verify(exactly = 1) { rankingRepository.getScore(any(), productId) }
    }

    @Test
    @DisplayName("랭킹에 없는 상품 조회 - null 반환")
    fun `should return null for product not in ranking`() {
        // given
        val productId = 999L
        val window = TimeWindow.DAILY

        every { rankingRepository.getRank(any(), productId) } returns null

        // when
        val ranking = rankingService.getProductRanking(productId, window)

        // then
        assertThat(ranking).isNull()

        verify(exactly = 1) { rankingRepository.getRank(any(), productId) }
        verify(exactly = 0) { rankingRepository.getScore(any(), productId) }
    }

    @Test
    @DisplayName("상품 ID 목록으로 상품 조회 - 맵 반환")
    fun `should find products by ids and return map`() {
        // given
        val productIds = listOf(100L, 101L, 102L)
        val products = productIds.map { id ->
            mockk<Product> {
                every { this@mockk.id } returns id
            }
        }

        every { productRepository.findAllById(productIds) } returns products

        // when
        val productMap = rankingService.findProductsByIds(productIds)

        // then
        assertThat(productMap).hasSize(3)
        assertThat(productMap.keys).containsExactlyInAnyOrder(100L, 101L, 102L)

        verify(exactly = 1) { productRepository.findAllById(productIds) }
    }
}
