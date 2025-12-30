package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankMonthlyRepository
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.ProductRankWeeklyRepository
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScope
import com.loopers.domain.ranking.RankingScore
import com.loopers.domain.ranking.TimeWindow
import com.loopers.interfaces.api.ranking.RankingV1Dto
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ContextConfiguration(initializers = [RedisTestContainersConfig::class])
@Sql(scripts = ["/ranking-tables.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val rankingRepository: RankingRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private lateinit var brand: Brand
    private lateinit var product1: Product
    private lateinit var product2: Product
    private lateinit var product3: Product

    @BeforeEach
    fun setUp() {
        // 브랜드 생성
        brand = brandRepository.save(
            Brand(
                name = "테스트 브랜드",
                description = "테스트용 브랜드",
            ),
        )

        // 상품 생성
        product1 = productRepository.save(
            Product(
                name = "상품 1",
                price = Price.Companion(BigDecimal("10000"), Currency.KRW),
                brand = brand,
            ),
        )

        product2 = productRepository.save(
            Product(
                name = "상품 2",
                price = Price.Companion(BigDecimal("20000"), Currency.KRW),
                brand = brand,
            ),
        )

        product3 = productRepository.save(
            Product(
                name = "상품 3",
                price = Price.Companion(BigDecimal("30000"), Currency.KRW),
                brand = brand,
            ),
        )

        // 일간 랭킹 데이터 생성
        val dailyKey = RankingKey.currentDaily(RankingScope.ALL)
        rankingRepository.incrementScore(dailyKey, product1.id, RankingScore(10.0))
        rankingRepository.incrementScore(dailyKey, product2.id, RankingScore(8.5))
        rankingRepository.incrementScore(dailyKey, product3.id, RankingScore(7.2))
        rankingRepository.setExpire(dailyKey)
    }

    @AfterEach
    fun tearDown() {
        // Redis 데이터 정리
        val dailyKey = RankingKey.currentDaily(RankingScope.ALL)
        redisTemplate.delete(dailyKey.toRedisKey())

        // 주간/월간 랭킹 데이터 정리 (테스트 실패 시에도 정리되도록)
        productRankWeeklyRepository.deleteAll()
        productRankMonthlyRepository.deleteAll()

        // DB 데이터 정리
        databaseCleanUp.truncateAllTables()
    }

    @Test
    @DisplayName("일간 랭킹 조회 - 상품 정보 포함")
    fun `should get daily rankings with product info`() {
        // given
        val today = RankingKey.dateToString(LocalDate.now())

        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=DAILY&date=$today&page=1&size=20",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.window).isEqualTo(TimeWindow.DAILY)
        Assertions.assertThat(body.timestamp).isEqualTo(today)
        Assertions.assertThat(body.page).isEqualTo(1)
        Assertions.assertThat(body.size).isEqualTo(20)
        Assertions.assertThat(body.totalCount).isEqualTo(3)

        // 랭킹 순서 검증
        Assertions.assertThat(body.rankings).hasSize(3)
        Assertions.assertThat(body.rankings[0].rank).isEqualTo(1)
        Assertions.assertThat(body.rankings[0].product.id).isEqualTo(product1.id)
        Assertions.assertThat(body.rankings[0].product.name).isEqualTo("상품 1")
        Assertions.assertThat(body.rankings[0].score).isEqualTo(10.0)

        Assertions.assertThat(body.rankings[1].rank).isEqualTo(2)
        Assertions.assertThat(body.rankings[1].product.id).isEqualTo(product2.id)

        Assertions.assertThat(body.rankings[2].rank).isEqualTo(3)
        Assertions.assertThat(body.rankings[2].product.id).isEqualTo(product3.id)
    }

    @Test
    @DisplayName("일간 랭킹 조회 - 기본값 사용 (오늘)")
    fun `should get daily rankings with default date`() {
        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=DAILY",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.rankings).hasSize(3)
    }

    @Test
    @DisplayName("일간 랭킹 조회 - 2페이지 (빈 결과)")
    fun `should get empty rankings for page 2`() {
        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=DAILY&page=2&size=20",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.page).isEqualTo(2)
        Assertions.assertThat(body.rankings).isEmpty()
        Assertions.assertThat(body.totalCount).isEqualTo(3) // 전체 개수는 여전히 3
    }

    @Test
    @DisplayName("일간 랭킹 조회 - 페이지 크기 제한")
    fun `should get rankings with limited size`() {
        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=DAILY&page=1&size=2",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.rankings).hasSize(2) // 요청한 크기만큼만
        Assertions.assertThat(body.size).isEqualTo(2)
        Assertions.assertThat(body.totalCount).isEqualTo(3)
    }

    @Test
    @DisplayName("시간별 랭킹 조회")
    fun `should get hourly rankings`() {
        // given
        val hourlyKey = RankingKey.currentHourly(RankingScope.ALL)
        rankingRepository.incrementScore(hourlyKey, product1.id, RankingScore(5.0))
        rankingRepository.incrementScore(hourlyKey, product2.id, RankingScore(3.5))
        rankingRepository.setExpire(hourlyKey)

        val currentHour = RankingKey.dateTimeToString(LocalDateTime.now())

        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=HOURLY&date=$currentHour&page=1&size=20",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.window).isEqualTo(TimeWindow.HOURLY)
        Assertions.assertThat(body.rankings).hasSize(2)
        Assertions.assertThat(body.rankings[0].product.id).isEqualTo(product1.id)

        // cleanup
        redisTemplate.delete(hourlyKey.toRedisKey())
    }

    @Test
    @DisplayName("랭킹 데이터가 없는 날짜 조회 - 빈 결과")
    fun `should get empty rankings for date without data`() {
        // given
        val futureDate = RankingKey.dateToString(LocalDate.now().plusDays(7))

        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=DAILY&date=$futureDate&page=1&size=20",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.rankings).isEmpty()
        Assertions.assertThat(body.totalCount).isEqualTo(0)
    }

    @Test
    @DisplayName("주간 랭킹 조회 - 상품 정보 포함")
    fun `should get weekly rankings with product info`() {
        // given: 주간 랭킹 데이터 생성
        val yearWeek = "2025W01"
        val periodStart = LocalDate.of(2024, 12, 30)
        val periodEnd = LocalDate.of(2025, 1, 5)

        val weeklyRankings = listOf(
            ProductRankWeekly(
                yearWeek = yearWeek,
                productId = product1.id,
                score = 10.0,
                rank = 1,
                periodStart = periodStart,
                periodEnd = periodEnd,
            ),
            ProductRankWeekly(
                yearWeek = yearWeek,
                productId = product2.id,
                score = 8.5,
                rank = 2,
                periodStart = periodStart,
                periodEnd = periodEnd,
            ),
            ProductRankWeekly(
                yearWeek = yearWeek,
                productId = product3.id,
                score = 7.2,
                rank = 3,
                periodStart = periodStart,
                periodEnd = periodEnd,
            ),
        )

        productRankWeeklyRepository.saveAll(weeklyRankings)

        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=WEEKLY&date=$yearWeek&page=1&size=20",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.window).isEqualTo(TimeWindow.WEEKLY)
        Assertions.assertThat(body.timestamp).isEqualTo(yearWeek)
        Assertions.assertThat(body.page).isEqualTo(1)
        Assertions.assertThat(body.size).isEqualTo(20)
        Assertions.assertThat(body.totalCount).isEqualTo(3)

        // 랭킹 순서 검증
        Assertions.assertThat(body.rankings).hasSize(3)
        Assertions.assertThat(body.rankings[0].rank).isEqualTo(1)
        Assertions.assertThat(body.rankings[0].product.id).isEqualTo(product1.id)
        Assertions.assertThat(body.rankings[0].product.name).isEqualTo("상품 1")
        Assertions.assertThat(body.rankings[0].score).isEqualTo(10.0)

        Assertions.assertThat(body.rankings[1].rank).isEqualTo(2)
        Assertions.assertThat(body.rankings[1].product.id).isEqualTo(product2.id)

        Assertions.assertThat(body.rankings[2].rank).isEqualTo(3)
        Assertions.assertThat(body.rankings[2].product.id).isEqualTo(product3.id)

        // cleanup
        productRankWeeklyRepository.deleteByYearWeek(yearWeek)
    }

    @Test
    @DisplayName("월간 랭킹 조회 - 상품 정보 포함")
    fun `should get monthly rankings with product info`() {
        // given: 월간 랭킹 데이터 생성
        val yearMonth = "202501"
        val periodStart = LocalDate.of(2025, 1, 1)
        val periodEnd = LocalDate.of(2025, 1, 31)

        val monthlyRankings = listOf(
            ProductRankMonthly(
                yearMonth = yearMonth,
                productId = product1.id,
                score = 15.0,
                rank = 1,
                periodStart = periodStart,
                periodEnd = periodEnd,
            ),
            ProductRankMonthly(
                yearMonth = yearMonth,
                productId = product2.id,
                score = 12.5,
                rank = 2,
                periodStart = periodStart,
                periodEnd = periodEnd,
            ),
        )

        productRankMonthlyRepository.saveAll(monthlyRankings)

        // when
        val responseType = object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {}
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?window=MONTHLY&date=$yearMonth&page=1&size=20",
            HttpMethod.GET,
            null,
            responseType,
        )

        // then
        Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        val apiResponse = response.body!!
        Assertions.assertThat(apiResponse.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

        val body = apiResponse.data!!
        Assertions.assertThat(body.window).isEqualTo(TimeWindow.MONTHLY)
        Assertions.assertThat(body.timestamp).isEqualTo(yearMonth)
        Assertions.assertThat(body.page).isEqualTo(1)
        Assertions.assertThat(body.size).isEqualTo(20)
        Assertions.assertThat(body.totalCount).isEqualTo(2)

        // 랭킹 순서 검증
        Assertions.assertThat(body.rankings).hasSize(2)
        Assertions.assertThat(body.rankings[0].rank).isEqualTo(1)
        Assertions.assertThat(body.rankings[0].product.id).isEqualTo(product1.id)
        Assertions.assertThat(body.rankings[0].product.name).isEqualTo("상품 1")
        Assertions.assertThat(body.rankings[0].score).isEqualTo(15.0)

        Assertions.assertThat(body.rankings[1].rank).isEqualTo(2)
        Assertions.assertThat(body.rankings[1].product.id).isEqualTo(product2.id)

        // cleanup
        productRankMonthlyRepository.deleteByYearMonth(yearMonth)
    }
}
