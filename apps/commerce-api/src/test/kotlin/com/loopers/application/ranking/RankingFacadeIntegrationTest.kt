package com.loopers.application.ranking

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightChangedEventV1
import com.loopers.domain.ranking.RankingWeightRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.math.BigDecimal

@SpringBootTest
@RecordApplicationEvents
class RankingFacadeIntegrationTest @Autowired constructor(
    private val rankingFacade: RankingFacade,
    private val rankingWeightRepository: RankingWeightRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val brandRepository: BrandRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("findRankings 통합 테스트")
    @Nested
    inner class FindRankings {

        @DisplayName("Redis에 랭킹 데이터가 있으면 상품 정보와 결합하여 반환한다")
        @Test
        fun `returns rankings with product details when data exists in Redis`() {
            // given
            val product1 = createProduct(name = "상품1", stockQuantity = 50)
            val product2 = createProduct(name = "상품2", stockQuantity = 30)
            val product3 = createProduct(name = "상품3", stockQuantity = 20)

            val bucketKey = RankingKeyGenerator.currentBucketKey()

            redisTemplate.opsForZSet().add(bucketKey, product3.id.toString(), 100.0)
            redisTemplate.opsForZSet().add(bucketKey, product1.id.toString(), 80.0)
            redisTemplate.opsForZSet().add(bucketKey, product2.id.toString(), 60.0)

            val criteria = RankingCriteria.FindRankings(
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).hasSize(3)
            assertThat(result.rankings[0].productId).isEqualTo(product3.id)
            assertThat(result.rankings[0].rank).isEqualTo(1)
            assertThat(result.rankings[0].name).isEqualTo("상품3")
            assertThat(result.rankings[0].stock).isEqualTo(20)
            assertThat(result.rankings[0].likeCount).isEqualTo(0L)
            assertThat(result.rankings[1].productId).isEqualTo(product1.id)
            assertThat(result.rankings[1].rank).isEqualTo(2)
            assertThat(result.rankings[1].stock).isEqualTo(50)
            assertThat(result.rankings[2].productId).isEqualTo(product2.id)
            assertThat(result.rankings[2].rank).isEqualTo(3)
            assertThat(result.rankings[2].stock).isEqualTo(30)
        }

        @DisplayName("Redis에 데이터가 없으면 빈 목록을 반환한다")
        @Test
        fun `returns empty list when no data in Redis`() {
            // given
            val criteria = RankingCriteria.FindRankings(
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).isEmpty()
            assertThat(result.hasNext).isFalse()
        }

        @DisplayName("페이지네이션이 올바르게 동작한다")
        @Test
        fun `pagination works correctly`() {
            // given
            val products = (1..5).map { createProduct(name = "상품$it") }

            val bucketKey = RankingKeyGenerator.currentBucketKey()
            products.forEachIndexed { index, product ->
                redisTemplate.opsForZSet().add(bucketKey, product.id.toString(), (100 - index * 10).toDouble())
            }

            val criteria = RankingCriteria.FindRankings(
                date = null,
                page = 0,
                size = 2,
            )

            // when
            val result = rankingFacade.findRankings(criteria)

            // then
            assertThat(result.rankings).hasSize(2)
            assertThat(result.hasNext).isTrue()
        }
    }

    @DisplayName("findWeight 통합 테스트")
    @Nested
    inner class FindWeight {

        @DisplayName("저장된 가중치가 있으면 반환한다")
        @Test
        fun `returns weight when exists`() {
            // given
            rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.15"),
                    likeWeight = BigDecimal("0.25"),
                    orderWeight = BigDecimal("0.55"),
                ),
            )

            // when
            val result = rankingFacade.findWeight()

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.55"))
        }

        @DisplayName("저장된 가중치가 없으면 fallback 값을 반환한다")
        @Test
        fun `returns fallback weight when not exists`() {
            // given - 가중치 없음

            // when
            val result = rankingFacade.findWeight()

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.10"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.20"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.60"))
        }
    }

    @DisplayName("updateWeight 통합 테스트")
    @Nested
    inner class UpdateWeight {

        @DisplayName("가중치를 수정하면 DB에 저장된다")
        @Test
        fun `saves weight to database when updated`() {
            // given
            val criteria = RankingCriteria.UpdateWeight(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // when
            val result = rankingFacade.updateWeight(criteria)

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.40"))

            val savedWeight = rankingWeightRepository.findLatest()
            assertThat(savedWeight).isNotNull()
            assertThat(savedWeight!!.viewWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(savedWeight.likeWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(savedWeight.orderWeight).isEqualTo(BigDecimal("0.40"))
        }

        @DisplayName("가중치 수정 시 RankingWeightChangedEventV1 이벤트가 발행된다")
        @Test
        fun `publishes RankingWeightChangedEventV1 when weight is updated`() {
            // given
            val criteria = RankingCriteria.UpdateWeight(
                viewWeight = BigDecimal("0.25"),
                likeWeight = BigDecimal("0.35"),
                orderWeight = BigDecimal("0.40"),
            )

            // when
            rankingFacade.updateWeight(criteria)

            // then
            val events = applicationEvents.stream(RankingWeightChangedEventV1::class.java).toList()
            assertThat(events).hasSize(1)
        }

        @DisplayName("기존 가중치가 있으면 업데이트한다")
        @Test
        fun `updates existing weight`() {
            // given
            rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                ),
            )

            val criteria = RankingCriteria.UpdateWeight(
                viewWeight = BigDecimal("0.50"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.20"),
            )

            // when
            val result = rankingFacade.updateWeight(criteria)

            // then
            assertThat(result.viewWeight).isEqualTo(BigDecimal("0.50"))
            assertThat(result.likeWeight).isEqualTo(BigDecimal("0.30"))
            assertThat(result.orderWeight).isEqualTo(BigDecimal("0.20"))
        }
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))

        val product = Product.create(
            name = name,
            price = price,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }
}
