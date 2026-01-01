package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingPeriodType
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.item.Chunk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(RedisTestContainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
@DisplayName("RedisAggregationWriter")
class RedisAggregationWriterTest @Autowired constructor(
    private val redisTemplate: RedisTemplate<String, String>,
) {

    @BeforeEach
    fun setUp() {
        // 테스트 전 Redis 초기화
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Nested
    @DisplayName("WEEKLY 기간에서 write 메서드는")
    inner class WeeklyWriteTest {

        @DisplayName("ZINCRBY로 점수를 누적한다")
        @Test
        fun shouldAccumulateScoresWithZincrby() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val stagingKey = "ranking:products:weekly:20250108:staging"

            val chunk1 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("100.50")),
                    ScoreEntry(200L, BigDecimal("200.75")),
                ),
            )

            // 같은 상품 100 - 누적되어야 함
            val chunk2 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("50.25")),
                    ScoreEntry(300L, BigDecimal("300.00")),
                ),
            )

            // act
            writer.write(chunk1)
            writer.write(chunk2)

            // assert
            val zSetOps = redisTemplate.opsForZSet()

            // 상품 100: 100.50 + 50.25 = 150.75
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isEqualTo(150.75)

            // 상품 200: 200.75
            val score200 = zSetOps.score(stagingKey, "200")
            assertThat(score200).isEqualTo(200.75)

            // 상품 300: 300.00
            val score300 = zSetOps.score(stagingKey, "300")
            assertThat(score300).isEqualTo(300.0)
        }

        @DisplayName("스테이징 키에 TTL 24시간을 설정한다")
        @Test
        fun shouldSetTtlTo24Hours() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val stagingKey = "ranking:products:weekly:20250108:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val ttl = redisTemplate.getExpire(stagingKey, TimeUnit.HOURS)
            assertThat(ttl).isBetween(23L, 24L) // 약간의 시간 경과를 고려
        }

        @DisplayName("올바른 WEEKLY 스테이징 키 포맷을 사용한다")
        @Test
        fun shouldUseCorrectWeeklyStagingKeyFormat() {
            // arrange
            val baseDate = LocalDate.of(2025, 12, 25)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val expectedKey = "ranking:products:weekly:20251225:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("빈 chunk는 아무 작업도 하지 않는다")
        @Test
        fun shouldDoNothing_whenChunkIsEmpty() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 8)
            val writer = createWriter(baseDate, RankingPeriodType.WEEKLY)
            val stagingKey = "ranking:products:weekly:20250108:staging"

            val emptyChunk = Chunk<ScoreEntry>(emptyList())

            // act
            writer.write(emptyChunk)

            // assert
            val exists = redisTemplate.hasKey(stagingKey)
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("MONTHLY 기간에서 write 메서드는")
    inner class MonthlyWriteTest {

        @DisplayName("올바른 MONTHLY 스테이징 키 포맷을 사용한다")
        @Test
        fun shouldUseCorrectMonthlyStagingKeyFormat() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val writer = createWriter(baseDate, RankingPeriodType.MONTHLY)
            val expectedKey = "ranking:products:monthly:20250201:staging"

            val chunk = Chunk(
                listOf(ScoreEntry(100L, BigDecimal("100.00"))),
            )

            // act
            writer.write(chunk)

            // assert
            val exists = redisTemplate.hasKey(expectedKey)
            assertThat(exists).isTrue()
        }

        @DisplayName("ZINCRBY로 점수를 누적한다")
        @Test
        fun shouldAccumulateScoresWithZincrby() {
            // arrange
            val baseDate = LocalDate.of(2025, 2, 1)
            val writer = createWriter(baseDate, RankingPeriodType.MONTHLY)
            val stagingKey = "ranking:products:monthly:20250201:staging"

            val chunk1 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("500.00")),
                    ScoreEntry(200L, BigDecimal("300.00")),
                ),
            )

            val chunk2 = Chunk(
                listOf(
                    ScoreEntry(100L, BigDecimal("250.00")),
                ),
            )

            // act
            writer.write(chunk1)
            writer.write(chunk2)

            // assert
            val zSetOps = redisTemplate.opsForZSet()

            // 상품 100: 500.00 + 250.00 = 750.00
            val score100 = zSetOps.score(stagingKey, "100")
            assertThat(score100).isEqualTo(750.0)

            // 상품 200: 300.00
            val score200 = zSetOps.score(stagingKey, "200")
            assertThat(score200).isEqualTo(300.0)
        }
    }

    private fun createWriter(baseDate: LocalDate, periodType: RankingPeriodType): RedisAggregationWriter {
        return RedisAggregationWriter(redisTemplate, baseDate, periodType)
    }
}
