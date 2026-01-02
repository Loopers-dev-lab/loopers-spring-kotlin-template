package com.loopers.application.ranking

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.cache.CacheTemplate
import com.loopers.domain.product.ProductSaleStatus
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductView
import com.loopers.domain.ranking.ProductRanking
import com.loopers.domain.ranking.RankingCommand
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RankingFacadeTest {

    private val rankingService: RankingService = mockk()
    private val productService: ProductService = mockk()
    private val cacheTemplate: CacheTemplate = mockk(relaxed = true)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-01-02T10:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val rankingFacade = RankingFacade(rankingService, productService, cacheTemplate, clock)

    @DisplayName("findRankings 테스트")
    @Nested
    inner class FindRankings {

        @DisplayName("랭킹 조회 시 RankingService를 통해 랭킹을 가져오고 상품 정보와 결합하여 반환한다")
        @Test
        fun `combines rankings with product details`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
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
                createProductView(productId = 1L, productName = "상품1", stockQuantity = 50, likeCount = 10L),
                createProductView(productId = 2L, productName = "상품2", stockQuantity = 30, likeCount = 5L),
                createProductView(productId = 3L, productName = "상품3", stockQuantity = 20, likeCount = 3L),
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns rankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L, 3L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).hasSize(3)
            assertThat(result.rankings[0].rank).isEqualTo(1)
            assertThat(result.rankings[0].productId).isEqualTo(1L)
            assertThat(result.rankings[0].name).isEqualTo("상품1")
            assertThat(result.rankings[0].stock).isEqualTo(50)
            assertThat(result.rankings[0].likeCount).isEqualTo(10L)
            assertThat(result.hasNext).isFalse()
        }

        @DisplayName("랭킹이 비어있으면 빈 목록을 반환한다")
        @Test
        fun `returns empty list when rankings are empty`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
                date = "2025012614",
                page = 0,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

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
                period = "hourly",
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

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns rankings
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
                period = "hourly",
                date = "2025012614",
                page = 1,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify { rankingService.findRankings(match<RankingCommand.FindRankings> { it.page == 1 && it.size == 10 }) }
            assertThat(result.rankings).isEmpty()
        }

        @DisplayName("date가 null이면 현재 시간 버킷을 사용한다")
        @Test
        fun `uses current bucket when date is null`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = null,
                date = null,
                page = 0,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            rankingFacade.findRankings(criteria)

            // then
            verify { rankingService.findRankings(match<RankingCommand.FindRankings> { it.date == null }) }
        }

        @DisplayName("상품 정보를 랭킹 순서대로 반환한다")
        @Test
        fun `returns products in ranking order`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
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

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns rankings
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

        @DisplayName("period=daily인 경우 일별 랭킹을 조회한다")
        @Test
        fun `queries daily rankings when period is daily`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "daily",
                date = null,
                page = 0,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            rankingFacade.findRankings(criteria)

            // then
            verify {
                rankingService.findRankings(
                    match<RankingCommand.FindRankings> {
                        it.period.name == "DAILY"
                    },
                )
            }
        }

        @DisplayName("period가 null이면 hourly로 조회한다")
        @Test
        fun `defaults to hourly when period is null`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = null,
                date = null,
                page = 0,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            rankingFacade.findRankings(criteria)

            // then
            verify {
                rankingService.findRankings(
                    match<RankingCommand.FindRankings> {
                        it.period.name == "HOURLY"
                    },
                )
            }
        }
    }

    @DisplayName("Cache-Aside 테스트")
    @Nested
    inner class CacheAsideTests {

        @DisplayName("HOURLY 조회 시 캐시를 사용하지 않는다")
        @Test
        fun `HOURLY bypasses cache`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "hourly",
                date = "2026010214",
                page = 0,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            rankingFacade.findRankings(criteria)

            // then
            verify(exactly = 0) { cacheTemplate.get(any(), any<TypeReference<CachedRankingV1>>()) }
            verify { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
        }

        @DisplayName("DAILY 조회 시 캐시를 사용하지 않는다")
        @Test
        fun `DAILY bypasses cache`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "daily",
                date = "20260102",
                page = 0,
                size = 10,
            )

            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            rankingFacade.findRankings(criteria)

            // then
            verify(exactly = 0) { cacheTemplate.get(any(), any<TypeReference<CachedRankingV1>>()) }
            verify { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
        }

        @DisplayName("WEEKLY 캐시 히트 시 캐시된 데이터를 반환한다")
        @Test
        fun `WEEKLY cache hit returns cached data`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "weekly",
                date = "20260102",
                page = 0,
                size = 10,
            )
            val cachedRankings = CachedRankingV1(
                rankings = listOf(
                    CachedRankingV1.Entry(productId = 1L, rank = 1, score = BigDecimal("100.00")),
                    CachedRankingV1.Entry(productId = 2L, rank = 2, score = BigDecimal("90.00")),
                ),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
            )

            every { cacheTemplate.get(any<RankingCacheKeys.RankingList>(), any<TypeReference<CachedRankingV1>>()) } returns cachedRankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify(exactly = 0) { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
            assertThat(result.rankings).hasSize(2)
            assertThat(result.rankings[0].productId).isEqualTo(1L)
            assertThat(result.rankings[1].productId).isEqualTo(2L)
        }

        @DisplayName("WEEKLY 캐시 미스 시 서비스 호출 후 캐시에 저장한다")
        @Test
        fun `WEEKLY cache miss calls service and stores result`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "weekly",
                date = "20260102",
                page = 0,
                size = 10,
            )
            val rankings = listOf(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.00")),
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.00")),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
            )

            every { cacheTemplate.get(any<RankingCacheKeys.RankingList>(), any<TypeReference<CachedRankingV1>>()) } returns null
            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns rankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
            verify { cacheTemplate.put(any<RankingCacheKeys.RankingList>(), any<CachedRankingV1>()) }
            assertThat(result.rankings).hasSize(2)
        }

        @DisplayName("MONTHLY 캐시 히트 시 캐시된 데이터를 반환한다")
        @Test
        fun `MONTHLY cache hit returns cached data`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "monthly",
                date = "20260102",
                page = 0,
                size = 10,
            )
            val cachedRankings = CachedRankingV1(
                rankings = listOf(
                    CachedRankingV1.Entry(productId = 1L, rank = 1, score = BigDecimal("100.00")),
                    CachedRankingV1.Entry(productId = 2L, rank = 2, score = BigDecimal("90.00")),
                ),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
            )

            every { cacheTemplate.get(any<RankingCacheKeys.RankingList>(), any<TypeReference<CachedRankingV1>>()) } returns cachedRankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify(exactly = 0) { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
            assertThat(result.rankings).hasSize(2)
            assertThat(result.rankings[0].productId).isEqualTo(1L)
            assertThat(result.rankings[1].productId).isEqualTo(2L)
        }

        @DisplayName("MONTHLY 캐시 미스 시 서비스 호출 후 캐시에 저장한다")
        @Test
        fun `MONTHLY cache miss calls service and stores result`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "monthly",
                date = "20260102",
                page = 0,
                size = 10,
            )
            val rankings = listOf(
                ProductRanking(productId = 1L, rank = 1, score = BigDecimal("100.00")),
                ProductRanking(productId = 2L, rank = 2, score = BigDecimal("90.00")),
            )
            val productViews = listOf(
                createProductView(productId = 1L, productName = "상품1"),
                createProductView(productId = 2L, productName = "상품2"),
            )

            every { cacheTemplate.get(any<RankingCacheKeys.RankingList>(), any<TypeReference<CachedRankingV1>>()) } returns null
            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns rankings
            every { productService.findAllProductViewByIds(listOf(1L, 2L)) } returns productViews

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
            verify { cacheTemplate.put(any<RankingCacheKeys.RankingList>(), any<CachedRankingV1>()) }
            assertThat(result.rankings).hasSize(2)
        }

        @DisplayName("WEEKLY 캐시 미스 시 빈 결과도 캐시에 저장하지 않는다")
        @Test
        fun `WEEKLY cache miss with empty result does not store in cache`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                period = "weekly",
                date = "20260102",
                page = 0,
                size = 10,
            )

            every { cacheTemplate.get(any<RankingCacheKeys.RankingList>(), any<TypeReference<CachedRankingV1>>()) } returns null
            every { rankingService.findRankings(any<RankingCommand.FindRankings>()) } returns emptyList()

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            verify { rankingService.findRankings(any<RankingCommand.FindRankings>()) }
            verify(exactly = 0) { cacheTemplate.put(any<RankingCacheKeys.RankingList>(), any<CachedRankingV1>()) }
            assertThat(result.rankings).isEmpty()
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
