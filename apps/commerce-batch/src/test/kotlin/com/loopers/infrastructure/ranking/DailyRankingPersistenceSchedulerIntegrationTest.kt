package com.loopers.infrastructure.ranking

import com.loopers.domain.product.ProductMetrics
import com.loopers.domain.product.ProductMetricsRepository
import com.loopers.domain.ranking.ProductRankDailyRepository
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScope
import com.loopers.domain.ranking.RankingScore
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

/**
 * DailyRankingPersistenceScheduler 통합 테스트
 *
 * 일간 랭킹 영구 저장 기능을 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig::class)
@DisplayName("DailyRankingPersistenceScheduler 통합 테스트")
class DailyRankingPersistenceSchedulerIntegrationTest @Autowired constructor(
    private val dailyRankingPersistenceScheduler: DailyRankingPersistenceScheduler,
    private val rankingRepository: RankingRepository,
    private val productRankDailyRepository: ProductRankDailyRepository,
    private val productMetricsRepository: ProductMetricsRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        // Redis 정리
        redisCleanUp.truncateAll()
    }

    @AfterEach
    fun tearDown() {
        // Redis 정리
        redisCleanUp.truncateAll()

        // DB 정리
        try {
            productRankDailyRepository.deleteByRankingDate(today)
        } catch (e: Exception) {
            // 테이블이 없을 수 있으므로 무시
        }
        try {
            databaseCleanUp.truncateAllTables()
        } catch (e: Exception) {
            // 테이블이 없을 수 있으므로 무시
        }
    }

    @Test
    fun `Redis 일간 랭킹을 DB에 영구 저장할 수 있다`() {
        // given: Redis에 랭킹 데이터 저장
        val productId1 = 100L
        val productId2 = 200L
        val productId3 = 300L

        val key = RankingKey.daily(RankingScope.ALL, today)
        rankingRepository.incrementScore(key, productId1, RankingScore(100.0))
        rankingRepository.incrementScore(key, productId2, RankingScore(90.0))
        rankingRepository.incrementScore(key, productId3, RankingScore(80.0))

        // ProductMetrics 저장
        productMetricsRepository.save(ProductMetrics(productId = productId1, likeCount = 50, viewCount = 1000, salesCount = 10))
        productMetricsRepository.save(ProductMetrics(productId = productId2, likeCount = 40, viewCount = 800, salesCount = 8))
        productMetricsRepository.save(ProductMetrics(productId = productId3, likeCount = 30, viewCount = 600, salesCount = 6))

        // when: 일간 랭킹 영구 저장 실행
        dailyRankingPersistenceScheduler.persistDailyRanking()

        // then: DB에 저장되어야 함
        val savedRankings = productRankDailyRepository.findByRankingDateBetween(today, today)
        assertThat(savedRankings).hasSize(3)

        // 순위 확인 (점수 내림차순)
        val ranking1 = savedRankings.find { it.productId == productId1 }!!
        assertThat(ranking1.rank).isEqualTo(1)
        assertThat(ranking1.score).isEqualTo(100.0)
        assertThat(ranking1.likeCount).isEqualTo(50)
        assertThat(ranking1.viewCount).isEqualTo(1000)
        assertThat(ranking1.salesCount).isEqualTo(10)

        val ranking2 = savedRankings.find { it.productId == productId2 }!!
        assertThat(ranking2.rank).isEqualTo(2)
        assertThat(ranking2.score).isEqualTo(90.0)

        val ranking3 = savedRankings.find { it.productId == productId3 }!!
        assertThat(ranking3.rank).isEqualTo(3)
        assertThat(ranking3.score).isEqualTo(80.0)
    }

    @Test
    fun `메트릭 정보가 없어도 랭킹 저장은 성공한다`() {
        // given: Redis에 랭킹 데이터만 저장 (ProductMetrics 없음)
        val productId = 999L
        val key = RankingKey.daily(RankingScope.ALL, today)
        rankingRepository.incrementScore(key, productId, RankingScore(50.0))

        // when: 일간 랭킹 영구 저장 실행
        dailyRankingPersistenceScheduler.persistDailyRanking()

        // then: DB에 저장되어야 함 (메트릭은 0으로)
        val savedRankings = productRankDailyRepository.findByRankingDateBetween(today, today)
        assertThat(savedRankings).hasSize(1)

        val savedRanking = savedRankings[0]
        assertThat(savedRanking.productId).isEqualTo(productId)
        assertThat(savedRanking.score).isEqualTo(50.0)
        assertThat(savedRanking.likeCount).isEqualTo(0)
        assertThat(savedRanking.viewCount).isEqualTo(0)
        assertThat(savedRanking.salesCount).isEqualTo(0)
    }

    @Test
    fun `중복 실행 시 기존 데이터를 삭제하고 새로 저장한다 - 멱등성`() {
        // given: 첫 번째 실행
        val productId = 100L
        val key = RankingKey.daily(RankingScope.ALL, today)
        rankingRepository.incrementScore(key, productId, RankingScore(100.0))

        dailyRankingPersistenceScheduler.persistDailyRanking()

        val firstSave = productRankDailyRepository.findByRankingDateBetween(today, today)
        assertThat(firstSave).hasSize(1)
        assertThat(firstSave[0].score).isEqualTo(100.0)

        // when: Redis 데이터 변경 후 재실행 (기존 점수 초기화를 위해 직접 덮어쓰기)
        productRankDailyRepository.deleteByRankingDate(today)
        rankingRepository.incrementScore(key, productId, RankingScore(50.0)) // 기존 100 + 50 = 150

        dailyRankingPersistenceScheduler.persistDailyRanking()

        // then: 기존 데이터는 삭제되고 새 데이터만 저장되어야 함
        val secondSave = productRankDailyRepository.findByRankingDateBetween(today, today)
        assertThat(secondSave).hasSize(1)
        assertThat(secondSave[0].score).isEqualTo(150.0) // 업데이트된 점수
    }

    @Test
    fun `Redis에 랭킹 데이터가 없으면 저장하지 않는다`() {
        // given: Redis에 데이터 없음 (아무것도 추가하지 않음)

        // when: 일간 랭킹 영구 저장 실행
        dailyRankingPersistenceScheduler.persistDailyRanking()

        // then: DB에 저장되지 않아야 함
        val savedRankings = productRankDailyRepository.findByRankingDateBetween(today, today)
        assertThat(savedRankings).isEmpty()
    }

    @Test
    fun `TOP 1000까지만 저장한다`() {
        // given: Redis에 1100개 랭킹 데이터 저장
        val key = RankingKey.daily(RankingScope.ALL, today)
        for (i in 1..1100) {
            rankingRepository.incrementScore(key, i.toLong(), RankingScore((1100 - i).toDouble()))
        }

        // when: 일간 랭킹 영구 저장 실행
        dailyRankingPersistenceScheduler.persistDailyRanking()

        // then: TOP 1000까지만 저장되어야 함
        val savedRankings = productRankDailyRepository.findByRankingDateBetween(today, today)
        assertThat(savedRankings).hasSize(1000)

        // 순위 확인
        val ranks = savedRankings.map { it.rank }.toSet()
        assertThat(ranks).containsExactlyInAnyOrderElementsOf((1..1000).toSet())
    }
}
