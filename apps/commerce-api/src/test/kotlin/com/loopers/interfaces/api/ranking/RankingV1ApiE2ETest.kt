package com.loopers.interfaces.api.ranking

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightRepository
import com.loopers.infrastructure.ranking.MvProductRankMonthly
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository
import com.loopers.infrastructure.ranking.MvProductRankWeekly
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository
import com.loopers.infrastructure.ranking.RankingKeyGenerator
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val rankingWeightRepository: RankingWeightRepository,
    private val redisTemplate: StringRedisTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
) {

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    inner class GetRankings {

        @DisplayName("랭킹을 조회하면 200 OK와 랭킹 목록을 반환한다")
        @Test
        fun returnRankingList_whenRequestIsValid() {
            // given
            val brand = createBrand()
            val product1 = createProduct(brand = brand, name = "상품1")
            val product2 = createProduct(brand = brand, name = "상품2")

            val bucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            redisTemplate.opsForZSet().add(bucketKey, product1.id.toString(), 100.0)
            redisTemplate.opsForZSet().add(bucketKey, product2.id.toString(), 50.0)

            // when
            val response = getRankings()

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).hasSize(2) },
                { assertThat(response.body?.data?.rankings?.get(0)?.rank).isEqualTo(1) },
                { assertThat(response.body?.data?.rankings?.get(0)?.productId).isEqualTo(product1.id) },
                { assertThat(response.body?.data?.rankings?.get(1)?.rank).isEqualTo(2) },
                { assertThat(response.body?.data?.rankings?.get(1)?.productId).isEqualTo(product2.id) },
            )
        }

        @DisplayName("페이지네이션이 정상 동작한다")
        @Test
        fun returnPaginatedRankings_whenPageParametersAreProvided() {
            // given
            val brand = createBrand()
            repeat(5) { index ->
                val product = createProduct(brand = brand, name = "상품${index + 1}")
                val bucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
                redisTemplate.opsForZSet().add(bucketKey, product.id.toString(), (100 - index).toDouble())
            }

            // when
            val firstPage = getRankings(page = 0, size = 2)
            val secondPage = getRankings(page = 1, size = 2)

            // then
            assertAll(
                { assertThat(firstPage.body?.data?.rankings).hasSize(2) },
                { assertThat(firstPage.body?.data?.hasNext).isTrue() },
                { assertThat(secondPage.body?.data?.rankings).hasSize(2) },
                { assertThat(secondPage.body?.data?.hasNext).isTrue() },
            )
        }

        @DisplayName("랭킹이 없으면 빈 목록을 반환한다")
        @Test
        fun returnEmptyList_whenNoRankingsExist() {
            // when
            val response = getRankings()

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).isEmpty() },
                { assertThat(response.body?.data?.hasNext).isFalse() },
            )
        }

        @DisplayName("period=daily 조회 시 일별 랭킹을 반환한다")
        @Test
        fun returnDailyRankings_whenPeriodIsDaily() {
            // given
            val brand = createBrand()
            val product1 = createProduct(brand = brand, name = "상품1")
            val product2 = createProduct(brand = brand, name = "상품2")

            val dailyBucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.DAILY)
            redisTemplate.opsForZSet().add(dailyBucketKey, product2.id.toString(), 100.0)
            redisTemplate.opsForZSet().add(dailyBucketKey, product1.id.toString(), 50.0)

            // when
            val response = getRankings(period = "daily")

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).hasSize(2) },
                { assertThat(response.body?.data?.rankings?.get(0)?.rank).isEqualTo(1) },
                { assertThat(response.body?.data?.rankings?.get(0)?.productId).isEqualTo(product2.id) },
                { assertThat(response.body?.data?.rankings?.get(1)?.rank).isEqualTo(2) },
                { assertThat(response.body?.data?.rankings?.get(1)?.productId).isEqualTo(product1.id) },
            )
        }

        @DisplayName("period가 없으면 기본값 hourly로 조회한다")
        @Test
        fun defaultToHourly_whenPeriodIsNotProvided() {
            // given
            val brand = createBrand()
            val product1 = createProduct(brand = brand, name = "상품1")
            val product2 = createProduct(brand = brand, name = "상품2")

            val hourlyBucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            val dailyBucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.DAILY)

            // hourly 버킷에만 데이터 추가
            redisTemplate.opsForZSet().add(hourlyBucketKey, product1.id.toString(), 100.0)
            // daily 버킷에 다른 상품 추가
            redisTemplate.opsForZSet().add(dailyBucketKey, product2.id.toString(), 100.0)

            // when
            val response = getRankings() // period 없이 호출

            // then - hourly 데이터만 반환되어야 함
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).hasSize(1) },
                { assertThat(response.body?.data?.rankings?.get(0)?.productId).isEqualTo(product1.id) },
            )
        }

        @DisplayName("period=weekly 조회 시 주간 랭킹을 반환한다")
        @Test
        fun returnWeeklyRankings_whenPeriodIsWeekly() {
            // given
            val brand = createBrand()
            val product1 = createProduct(brand = brand, name = "상품1")
            val product2 = createProduct(brand = brand, name = "상품2")

            // Save weekly ranking data directly to database
            saveWeeklyRanking(product2.id, rank = 1, score = 100.0)
            saveWeeklyRanking(product1.id, rank = 2, score = 50.0)

            // when
            val response = getRankings(period = "weekly")

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).hasSize(2) },
                { assertThat(response.body?.data?.rankings?.get(0)?.rank).isEqualTo(1) },
                { assertThat(response.body?.data?.rankings?.get(0)?.productId).isEqualTo(product2.id) },
                { assertThat(response.body?.data?.rankings?.get(1)?.rank).isEqualTo(2) },
                { assertThat(response.body?.data?.rankings?.get(1)?.productId).isEqualTo(product1.id) },
            )
        }

        @DisplayName("period=monthly 조회 시 월간 랭킹을 반환한다")
        @Test
        fun returnMonthlyRankings_whenPeriodIsMonthly() {
            // given
            val brand = createBrand()
            val product1 = createProduct(brand = brand, name = "상품1")
            val product2 = createProduct(brand = brand, name = "상품2")
            val product3 = createProduct(brand = brand, name = "상품3")

            // Save monthly ranking data directly to database
            saveMonthlyRanking(product3.id, rank = 1, score = 150.0)
            saveMonthlyRanking(product1.id, rank = 2, score = 100.0)
            saveMonthlyRanking(product2.id, rank = 3, score = 50.0)

            // when
            val response = getRankings(period = "monthly")

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).hasSize(3) },
                { assertThat(response.body?.data?.rankings?.get(0)?.rank).isEqualTo(1) },
                { assertThat(response.body?.data?.rankings?.get(0)?.productId).isEqualTo(product3.id) },
                { assertThat(response.body?.data?.rankings?.get(1)?.rank).isEqualTo(2) },
                { assertThat(response.body?.data?.rankings?.get(1)?.productId).isEqualTo(product1.id) },
                { assertThat(response.body?.data?.rankings?.get(2)?.rank).isEqualTo(3) },
                { assertThat(response.body?.data?.rankings?.get(2)?.productId).isEqualTo(product2.id) },
            )
        }

        @DisplayName("weekly 랭킹이 없으면 빈 목록을 반환한다")
        @Test
        fun returnEmptyList_whenNoWeeklyRankingsExist() {
            // when
            val response = getRankings(period = "weekly")

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).isEmpty() },
                { assertThat(response.body?.data?.hasNext).isFalse() },
            )
        }

        @DisplayName("monthly 랭킹이 없으면 빈 목록을 반환한다")
        @Test
        fun returnEmptyList_whenNoMonthlyRankingsExist() {
            // when
            val response = getRankings(period = "monthly")

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.rankings).isEmpty() },
                { assertThat(response.body?.data?.hasNext).isFalse() },
            )
        }
    }

    @DisplayName("GET /api/v1/rankings/weight")
    @Nested
    inner class GetWeight {

        @DisplayName("가중치를 조회하면 200 OK와 가중치 정보를 반환한다")
        @Test
        fun returnWeight_whenRequestIsValid() {
            // given
            createRankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // when
            val response = getWeight()

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.viewWeight).isEqualByComparingTo(BigDecimal("0.10")) },
                { assertThat(response.body?.data?.likeWeight).isEqualByComparingTo(BigDecimal("0.20")) },
                { assertThat(response.body?.data?.orderWeight).isEqualByComparingTo(BigDecimal("0.60")) },
            )
        }

        @DisplayName("가중치가 없으면 기본값을 반환한다")
        @Test
        fun returnFallbackWeight_whenNoWeightExists() {
            // when
            val response = getWeight()

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.viewWeight).isEqualByComparingTo(BigDecimal("0.10")) },
                { assertThat(response.body?.data?.likeWeight).isEqualByComparingTo(BigDecimal("0.20")) },
                { assertThat(response.body?.data?.orderWeight).isEqualByComparingTo(BigDecimal("0.60")) },
            )
        }
    }

    @DisplayName("PUT /api/v1/rankings/weight")
    @Nested
    inner class UpdateWeight {

        @DisplayName("가중치를 수정하면 200 OK와 수정된 가중치를 반환한다")
        @Test
        fun returnUpdatedWeight_whenRequestIsValid() {
            // given
            createRankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
            val request = mapOf(
                "viewWeight" to "0.30",
                "likeWeight" to "0.40",
                "orderWeight" to "0.50",
            )

            // when
            val response = updateWeight(request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.viewWeight).isEqualByComparingTo(BigDecimal("0.30")) },
                { assertThat(response.body?.data?.likeWeight).isEqualByComparingTo(BigDecimal("0.40")) },
                { assertThat(response.body?.data?.orderWeight).isEqualByComparingTo(BigDecimal("0.50")) },
            )
        }

        @DisplayName("가중치가 0~1 범위를 벗어나면 에러를 반환한다")
        @Test
        fun returnError_whenWeightIsOutOfRange() {
            // given
            createRankingWeight()
            val request = mapOf(
                "viewWeight" to "1.50",
                "likeWeight" to "0.20",
                "orderWeight" to "0.60",
            )

            // when
            val response = updateWeight(request)

            // then
            assertThat(response.statusCode.is2xxSuccessful).isFalse()
        }
    }

    private fun createBrand(name: String = "테스트 브랜드"): Brand {
        return brandRepository.save(Brand.create(name))
    }

    private fun createProduct(
        brand: Brand,
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
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

    private fun createRankingWeight(
        viewWeight: BigDecimal = BigDecimal("0.10"),
        likeWeight: BigDecimal = BigDecimal("0.20"),
        orderWeight: BigDecimal = BigDecimal("0.60"),
    ): RankingWeight {
        return rankingWeightRepository.save(
            RankingWeight.create(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                orderWeight = orderWeight,
            ),
        )
    }

    private fun getRankings(
        period: String? = null,
        date: String? = null,
        page: Int? = null,
        size: Int? = null,
    ): ResponseEntity<ApiResponse<RankingV1Response.GetRankings>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val queryParams = buildString {
            val params = mutableListOf<String>()
            period?.let { params.add("period=$it") }
            date?.let { params.add("date=$it") }
            page?.let { params.add("page=$it") }
            size?.let { params.add("size=$it") }
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }

        return testRestTemplate.exchange(
            "/api/v1/rankings$queryParams",
            HttpMethod.GET,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<RankingV1Response.GetRankings>>() {},
        )
    }

    private fun getWeight(): ResponseEntity<ApiResponse<RankingV1Response.GetWeight>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        return testRestTemplate.exchange(
            "/api/v1/rankings/weight",
            HttpMethod.GET,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<RankingV1Response.GetWeight>>() {},
        )
    }

    private fun updateWeight(
        request: Map<String, String>,
    ): ResponseEntity<ApiResponse<RankingV1Response.UpdateWeight>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        return testRestTemplate.exchange(
            "/api/v1/rankings/weight",
            HttpMethod.PUT,
            HttpEntity(request, headers),
            object : ParameterizedTypeReference<ApiResponse<RankingV1Response.UpdateWeight>>() {},
        )
    }

    private fun saveWeeklyRanking(
        productId: Long,
        rank: Int,
        score: Double,
        baseDate: LocalDate = LocalDate.now(SEOUL_ZONE),
    ): MvProductRankWeekly {
        return weeklyJpaRepository.save(
            MvProductRankWeekly(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = BigDecimal.valueOf(score),
            ),
        )
    }

    private fun saveMonthlyRanking(
        productId: Long,
        rank: Int,
        score: Double,
        baseDate: LocalDate = LocalDate.now(SEOUL_ZONE),
    ): MvProductRankMonthly {
        return monthlyJpaRepository.save(
            MvProductRankMonthly(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = BigDecimal.valueOf(score),
            ),
        )
    }
}
