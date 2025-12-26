package com.loopers.application.ranking

import com.loopers.domain.product.ProductSaleStatus
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductView
import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingService
import com.loopers.domain.ranking.RankingWeight
import com.loopers.support.values.Money
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RankingFacadeTest {

    private val rankingService: RankingService = mockk()
    private val productService: ProductService = mockk()
    private val productRankingReader: ProductRankingReader = mockk()
    private val rankingFacade = RankingFacade(rankingService, productService, productRankingReader)

    @DisplayName("findRankings 테스트")
    @Nested
    inner class FindRankings {

        @DisplayName("랭킹 조회 시 Redis에서 productId를 가져오고 상품 정보와 결합하여 반환한다")
        @Test
        fun `combines productIds with product details`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = "2025012614",
                page = 0,
                size = 10,
            )
            val rankings = listOf(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.00")),
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.00")),
                ProductRanking(productId = 3L, rank = 3, score = BigDecimal("80.00")),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
                createProductView(productId = 3L, productName = "상품3"),
            )

            every { productRankingReader.getTopRankings("ranking:hourly:2025012614", 0L, 11L) } returns rankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L, 3L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).hasSize(3)
            assertThat(result.rankings[0].rank).isEqualTo(1)
            assertThat(result.rankings[0].productId).isEqualTo(1L)
            assertThat(result.rankings[0].name).isEqualTo("상품1")
            assertThat(result.rankings[0].score).isEqualTo(BigDecimal("100.00"))
            assertThat(result.hasNext).isFalse()
        }

        @DisplayName("랭킹이 비어있으면 빈 목록을 반환한다")
        @Test
        fun `returns empty list when rankings are empty`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = "2025012614",
                page = 0,
                size = 10,
            )

            every { productRankingReader.getTopRankings("ranking:hourly:2025012614", 0L, 11L) } returns emptyList()

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).isEmpty()
            assertThat(result.hasNext).isFalse()
        }

        @DisplayName("다음 페이지가 있으면 hasNext가 true이다")
        @Test
        fun `hasNext is true when there are more results`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = "2025012614",
                page = 0,
                size = 2,
            )
            val rankings = listOf(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.00")),
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.00")),
                ProductRanking(productId = 3L, rank = 3, score = BigDecimal("80.00")),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
            )

            every { productRankingReader.getTopRankings("ranking:hourly:2025012614", 0L, 3L) } returns rankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).hasSize(2)
            assertThat(result.hasNext).isTrue()
        }

        @DisplayName("페이지네이션이 올바르게 동작한다")
        @Test
        fun `pagination works correctly`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = "2025012614",
                page = 1,
                size = 10,
            )

            every { productRankingReader.getTopRankings("ranking:hourly:2025012614", 10L, 11L) } returns emptyList()

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify { productRankingReader.getTopRankings("ranking:hourly:2025012614", 10L, 11L) }
            assertThat(result.rankings).isEmpty()
        }

        @DisplayName("date가 null이면 현재 시간 버킷을 사용한다")
        @Test
        fun `uses current bucket when date is null`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = null,
                page = 0,
                size = 10,
            )

            every { productRankingReader.getTopRankings(any(), any(), any()) } returns emptyList()

            // when
            rankingFacade.findRankings(criteria)

            // then
            verify { productRankingReader.getTopRankings(match { it.startsWith("ranking:hourly:") }, 0L, 11L) }
        }

        @DisplayName("상품 정보를 랭킹 순서대로 반환한다")
        @Test
        fun `returns products in ranking order`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = "2025012614",
                page = 0,
                size = 10,
            )
            val rankings = listOf(
                ProductRanking(productId = 3L, rank = 1, score = BigDecimal("100.00")),
                ProductRanking(productId = 1L, rank = 2, score = BigDecimal("90.00")),
                ProductRanking(productId = 2L, rank = 3, score = BigDecimal("80.00")),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
                createProductView(productId = 3L, productName = "상품3"),
            )

            every { productRankingReader.getTopRankings("ranking:hourly:2025012614", 0L, 11L) } returns rankings
            every { productService.findAllProductViewByIds(listOf(3L, 1L, 2L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).hasSize(3)
            assertThat(result.rankings[0].productId).isEqualTo(3L)
            assertThat(result.rankings[0].rank).isEqualTo(1)
            assertThat(result.rankings[1].productId).isEqualTo(1L)
            assertThat(result.rankings[1].rank).isEqualTo(2)
            assertThat(result.rankings[2].productId).isEqualTo(2L)
            assertThat(result.rankings[2].rank).isEqualTo(3)
        }
    }

    @DisplayName("findWeight 테스트")
    @Nested
    inner class FindWeight {

        @DisplayName("가중치를 조회하여 반환한다")
        @Test
        fun `returns weight`() {
            // given
            val weight = RankingWeight.create(
                viewWeight = BigDecimal("0.15"),
                likeWeight = BigDecimal("0.25"),
                orderWeight = BigDecimal("0.55"),
            )
            every { rankingService.findWeight() } returns weight

            // when
            val result = rankingFacade.findWeight()

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.55"))
        }
    }

    @DisplayName("updateWeight 테스트")
    @Nested
    inner class UpdateWeight {

        @DisplayName("가중치를 수정하고 결과를 반환한다")
        @Test
        fun `updates weight and returns result`() {
            // given
            val criteria = RankingCriteria.UpdateWeight(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )
            val updatedWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )
            every {
                rankingService.updateWeight(
                    viewWeight = BigDecimal("0.30"),
                    likeWeight = BigDecimal("0.30"),
                    orderWeight = BigDecimal("0.40"),
                )
            } returns updatedWeight

            // when
            val result = rankingFacade.updateWeight(criteria)

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.40"))
        }

        @DisplayName("RankingService.updateWeight가 호출된다")
        @Test
        fun `calls RankingService updateWeight`() {
            // given
            val criteria = RankingCriteria.UpdateWeight(
                viewWeight = BigDecimal("0.25"),
                likeWeight = BigDecimal("0.35"),
                orderWeight = BigDecimal("0.40"),
            )
            val updatedWeight = RankingWeight.create(
                viewWeight = BigDecimal("0.25"),
                likeWeight = BigDecimal("0.35"),
                orderWeight = BigDecimal("0.40"),
            )
            every {
                rankingService.updateWeight(
                    viewWeight = BigDecimal("0.25"),
                    likeWeight = BigDecimal("0.35"),
                    orderWeight = BigDecimal("0.40"),
                )
            } returns updatedWeight

            // when
            rankingFacade.updateWeight(criteria)

            // then
            verify {
                rankingService.updateWeight(
                    viewWeight = BigDecimal("0.25"),
                    likeWeight = BigDecimal("0.35"),
                    orderWeight = BigDecimal("0.40"),
                )
            }
        }
    }

    private fun createProductView(
        productId: Long,
        productName: String = "테스트 상품",
        price: Money = Money.krw(10000),
        status: ProductSaleStatus = ProductSaleStatus.ON_SALE,
        brandId: Long = 1L,
        brandName: String = "테스트 브랜드",
        stockQuantity: Int = 100,
        likeCount: Long = 0L,
    ): ProductView {
        return ProductView(
            productId = productId,
            productName = productName,
            price = price,
            status = status,
            brandId = brandId,
            brandName = brandName,
            stockQuantity = stockQuantity,
            likeCount = likeCount,
        )
    }
}
