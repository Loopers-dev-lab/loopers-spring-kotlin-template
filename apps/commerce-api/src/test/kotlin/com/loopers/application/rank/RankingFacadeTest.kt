package com.loopers.application.rank

import com.loopers.IntegrationTestSupport
import com.loopers.application.ranking.RankingCommand
import com.loopers.application.ranking.RankingFacade
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.common.vo.Money
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RankingFacadeTest(
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandJpaRepository,
    private val productRepository: ProductRepository,
    private val rankingFacade: RankingFacade,
    private val redisTemplate: RedisTemplate<String, String>,
) : IntegrationTestSupport() {

    @AfterEach
    fun teardown() {
        databaseCleanUp.truncateAllTables()
        // Redis 데이터 정리
        redisTemplate.keys("ranking:*")?.forEach { redisTemplate.delete(it) }
    }

    @DisplayName("랭킹 조회")
    @Nested
    inner class GetRanking {

        @DisplayName("랭킹이 정상적으로 조회됩니다.")
        @Test
        fun getRankingSuccess() {
            // arrange
            val date = LocalDate.parse("20251225", DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay()

            prepareProductData()
            prepareRedisRankingData(date)

            // act
            val pageable = PageRequest.of(0, 5)
            val command = RankingCommand.GetRankings(
                pageable, date,
            )

            val rankingInfo = rankingFacade.getRanking(command)

            // assert
            assertAll(
                { assertThat(rankingInfo.pageSize).isEqualTo(5) },
                { assertThat(rankingInfo.totalElements).isEqualTo(5L) },
                { assertThat(rankingInfo.items).hasSize(5) },
                // 순서 검증: 3, 4, 5, 2, 1
                { assertThat(rankingInfo.items[0].productId).isEqualTo(3L) },
                { assertThat(rankingInfo.items[1].productId).isEqualTo(4L) },
                { assertThat(rankingInfo.items[2].productId).isEqualTo(5L) },
                { assertThat(rankingInfo.items[3].productId).isEqualTo(2L) },
                { assertThat(rankingInfo.items[4].productId).isEqualTo(1L) },
            )
        }

        @DisplayName("Redis 에 저장이 되어 있지 않은 경우, 빈 배열을 Return 합니다.")
        @Test
        fun getEmptyRankingList_whenRedisIsNotApplied() {
            // arrange
            prepareProductData()

            // act
            val pageable = PageRequest.of(0, 5)
            val command = RankingCommand.GetRankings(
                pageable, LocalDate.parse("20251225", DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(),
            )

            val rankingInfo = rankingFacade.getRanking(command)

            // assert
            assertAll(
                { assertThat(rankingInfo.pageNumber).isEqualTo(0) },
                { assertThat(rankingInfo.totalElements).isEqualTo(0L) },
                { assertThat(rankingInfo.items).isEmpty() },
            )
        }
    }

    private fun prepareProductData() {
        // 브랜드 생성
        val brand1 = BrandModel("Nike")
        val brand2 = BrandModel("Adidas")
        brandRepository.save(brand1)
        brandRepository.save(brand2)

        // Nike 상품 3개 생성
        val nikeProduct1 = ProductModel.create(
            name = "Nike Air Max",
            price = Money(BigDecimal.valueOf(150000)),
            refBrandId = brand1.id,
        )
        val nikeProduct2 = ProductModel.create(
            name = "Nike Jordan",
            price = Money(BigDecimal.valueOf(200000)),
            refBrandId = brand1.id,
        )
        val nikeProduct3 = ProductModel.create(
            name = "Nike Dunk",
            price = Money(BigDecimal.valueOf(120000)),
            refBrandId = brand1.id,
        )
        productRepository.save(nikeProduct1)
        productRepository.save(nikeProduct2)
        productRepository.save(nikeProduct3)

        // Adidas 상품 2개 생성
        val adidasProduct1 = ProductModel.create(
            name = "Adidas Superstar",
            price = Money(BigDecimal.valueOf(100000)),
            refBrandId = brand2.id,
        )
        val adidasProduct2 = ProductModel.create(
            name = "Adidas Stan Smith",
            price = Money(BigDecimal.valueOf(90000)),
            refBrandId = brand2.id,
        )
        productRepository.save(adidasProduct1)
        productRepository.save(adidasProduct2)
    }

    private fun prepareRedisRankingData(date: LocalDateTime) {
        val zSetOps = redisTemplate.opsForZSet()
        val formattedDate = date.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val key = "ranking:all:$formattedDate"

        // 순서: 3, 4, 5, 2, 1 (score가 높을수록 상위 랭킹)
        zSetOps.add(key, "3", 100.0)
        zSetOps.add(key, "4", 90.0)
        zSetOps.add(key, "5", 80.0)
        zSetOps.add(key, "2", 70.0)
        zSetOps.add(key, "1", 60.0)
    }
}
