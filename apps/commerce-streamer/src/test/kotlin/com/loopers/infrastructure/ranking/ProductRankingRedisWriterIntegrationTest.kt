package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingWriter
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.Score
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

@SpringBootTest
@DisplayName("ProductRankingRedisWriter 통합 테스트")
class ProductRankingRedisWriterIntegrationTest @Autowired constructor(
    private val productRankingWriter: ProductRankingWriter,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()

    // Test dateTime values
    private val testHourlyDateTime = ZonedDateTime.of(2025, 1, 15, 14, 0, 0, 0, SEOUL_ZONE)
    private val testDailyDateTime = ZonedDateTime.of(2025, 1, 15, 0, 0, 0, 0, SEOUL_ZONE)

    // Expected bucket keys based on the dateTime values
    private val testHourlyBucketKey = "ranking:products:hourly:2025011514"
    private val testDailyBucketKey = "ranking:products:daily:20250115"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("replaceAll()")
    @Nested
    inner class ReplaceAll {

        @DisplayName("기존 점수를 모두 교체한다")
        @Test
        fun `replaces all existing scores`() {
            // given
            zSetOps.add(testHourlyBucketKey, "101", 100.0)
            zSetOps.add(testHourlyBucketKey, "102", 200.0)

            val newScores = mapOf(
                103L to Score.of(500.0),
                104L to Score.of(600.0),
            )

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, newScores)

            // then - old entries should be removed
            val score101: Double? = zSetOps.score(testHourlyBucketKey, "101")
            val score102: Double? = zSetOps.score(testHourlyBucketKey, "102")
            assertThat(score101).isNull()
            assertThat(score102).isNull()

            // new entries should exist
            assertThat(zSetOps.score(testHourlyBucketKey, "103")).isEqualTo(500.0)
            assertThat(zSetOps.score(testHourlyBucketKey, "104")).isEqualTo(600.0)
        }

        @DisplayName("빈 scores 맵은 버킷을 삭제한다")
        @Test
        fun `deletes bucket when scores map is empty`() {
            // given
            zSetOps.add(testHourlyBucketKey, "101", 100.0)
            assertThat(redisTemplate.hasKey(testHourlyBucketKey)).isTrue()

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, emptyMap())

            // then
            assertThat(redisTemplate.hasKey(testHourlyBucketKey)).isFalse()
        }

        @DisplayName("100개를 초과하는 상품이 있을 경우 상위 100개만 유지한다")
        @Test
        fun `keeps only top 100 items when more than 100 are written`() {
            // given - 150개 상품 생성 (점수: 1.0 ~ 150.0)
            val scores = (1L..150L).associate { productId ->
                productId to Score.of(productId.toDouble())
            }

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, scores)

            // then - 버킷에는 100개만 존재해야 함
            val bucketSize = zSetOps.size(testHourlyBucketKey)
            assertThat(bucketSize).isEqualTo(100)

            // 상위 100개 (점수 51~150) 만 존재해야 함
            // 점수 1~50은 삭제되어야 함
            for (productId in 1L..50L) {
                val score: Double? = zSetOps.score(testHourlyBucketKey, productId.toString())
                assertThat(score).withFailMessage("Product $productId should be removed").isNull()
            }

            // 점수 51~150은 존재해야 함
            for (productId in 51L..150L) {
                val score: Double? = zSetOps.score(testHourlyBucketKey, productId.toString())
                assertThat(score).withFailMessage("Product $productId should exist with score ${productId.toDouble()}").isNotNull()
                assertThat(score).isEqualTo(productId.toDouble())
            }
        }
    }

    @DisplayName("Atomic Transition (FR-4, AC-8)")
    @Nested
    inner class AtomicTransition {

        @DisplayName("staging key에 작성 후 RENAME으로 active key로 원자적 교체한다")
        @Test
        fun `writes to staging key then renames to active key`() {
            // given
            val stagingKey = "$testHourlyBucketKey:staging"
            val scores = mapOf(
                101L to Score.of(100.0),
                102L to Score.of(200.0),
            )

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, scores)

            // then - staging key should not exist after RENAME
            assertThat(redisTemplate.hasKey(stagingKey)).isFalse()

            // active key should have the data
            assertThat(zSetOps.score(testHourlyBucketKey, "101")).isEqualTo(100.0)
            assertThat(zSetOps.score(testHourlyBucketKey, "102")).isEqualTo(200.0)
        }

        @DisplayName("기존 데이터를 원자적으로 교체한다 (부분 데이터 노출 없음)")
        @Test
        fun `atomically replaces existing data without partial data exposure`() {
            // given - 기존 데이터가 있는 상태
            zSetOps.add(testHourlyBucketKey, "1", 10.0)
            zSetOps.add(testHourlyBucketKey, "2", 20.0)
            zSetOps.add(testHourlyBucketKey, "3", 30.0)

            val newScores = mapOf(
                101L to Score.of(100.0),
                102L to Score.of(200.0),
            )

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, newScores)

            // then - 기존 데이터는 완전히 제거됨
            val score1: Double? = zSetOps.score(testHourlyBucketKey, "1")
            val score2: Double? = zSetOps.score(testHourlyBucketKey, "2")
            val score3: Double? = zSetOps.score(testHourlyBucketKey, "3")
            assertThat(score1).isNull()
            assertThat(score2).isNull()
            assertThat(score3).isNull()

            // 새 데이터만 존재
            assertThat(zSetOps.size(testHourlyBucketKey)).isEqualTo(2)
            val score101: Double? = zSetOps.score(testHourlyBucketKey, "101")
            val score102: Double? = zSetOps.score(testHourlyBucketKey, "102")
            assertThat(score101).isEqualTo(100.0)
            assertThat(score102).isEqualTo(200.0)
        }

        @DisplayName("이미 존재하는 staging key를 정리 후 새 데이터를 작성한다")
        @Test
        fun `handles already existing staging key by clearing it before writing`() {
            // given - 이전 실패로 staging key가 남아있는 상태
            val stagingKey = "$testHourlyBucketKey:staging"
            zSetOps.add(stagingKey, "old", 999.0)
            assertThat(redisTemplate.hasKey(stagingKey)).isTrue()

            val newScores = mapOf(101L to Score.of(100.0))

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, newScores)

            // then - staging key 정리 후 RENAME 완료
            assertThat(redisTemplate.hasKey(stagingKey)).isFalse()
            val score101: Double? = zSetOps.score(testHourlyBucketKey, "101")
            val scoreOld: Double? = zSetOps.score(testHourlyBucketKey, "old")
            assertThat(score101).isEqualTo(100.0)
            assertThat(scoreOld).isNull()
        }
    }

    @DisplayName("TTL (BR-2)")
    @Nested
    inner class TtlSettings {

        @DisplayName("HOURLY 버킷은 2시간 TTL이 설정된다")
        @Test
        fun `sets 2 hour TTL for hourly bucket`() {
            // given
            val scores = mapOf(101L to Score.of(100.0))

            // when
            productRankingWriter.replaceAll(RankingPeriod.HOURLY, testHourlyDateTime, scores)

            // then - HOURLY TTL은 2시간(7200초)
            val ttl = redisTemplate.getExpire(testHourlyBucketKey, TimeUnit.SECONDS)
            assertThat(ttl).isGreaterThan(7100L)
            assertThat(ttl).isLessThanOrEqualTo(7200L)
        }

        @DisplayName("DAILY 버킷은 48시간 TTL이 설정된다")
        @Test
        fun `sets 48 hour TTL for daily bucket`() {
            // given
            val scores = mapOf(101L to Score.of(100.0))

            // when
            productRankingWriter.replaceAll(RankingPeriod.DAILY, testDailyDateTime, scores)

            // then - DAILY TTL은 48시간(172800초)
            val ttl = redisTemplate.getExpire(testDailyBucketKey, TimeUnit.SECONDS)
            assertThat(ttl).isGreaterThan(172700L)
            assertThat(ttl).isLessThanOrEqualTo(172800L)
        }
    }
}
