package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingWriter
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
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@SpringBootTest
@DisplayName("ProductRankingRedisWriter 통합 테스트")
class ProductRankingRedisWriterIntegrationTest @Autowired constructor(
    private val productRankingWriter: ProductRankingWriter,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()
    private val testBucketKey = "ranking:hourly:2025011514"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("incrementScores()")
    @Nested
    inner class IncrementScores {

        @DisplayName("Pipeline을 사용하여 여러 상품의 점수를 일괄 증분한다")
        @Test
        fun `increments multiple product scores using pipeline`() {
            // given
            val deltas = mapOf(
                101L to Score.of(100.0),
                102L to Score.of(200.0),
                103L to Score.of(300.0),
            )

            // when
            productRankingWriter.incrementScores(testBucketKey, deltas)

            // then
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(100.0)
            assertThat(zSetOps.score(testBucketKey, "102")).isEqualTo(200.0)
            assertThat(zSetOps.score(testBucketKey, "103")).isEqualTo(300.0)
        }

        @DisplayName("기존 점수에 증분값을 더한다")
        @Test
        fun `adds increment to existing score`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)
            val deltas = mapOf(101L to Score.of(50.0))

            // when
            productRankingWriter.incrementScores(testBucketKey, deltas)

            // then
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(150.0)
        }

        @DisplayName("여러 번 증분 호출 시 점수가 누적된다")
        @Test
        fun `accumulates scores on multiple increment calls`() {
            // given
            val deltas1 = mapOf(101L to Score.of(100.0))
            val deltas2 = mapOf(101L to Score.of(50.0))
            val deltas3 = mapOf(101L to Score.of(25.0))

            // when
            productRankingWriter.incrementScores(testBucketKey, deltas1)
            productRankingWriter.incrementScores(testBucketKey, deltas2)
            productRankingWriter.incrementScores(testBucketKey, deltas3)

            // then
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(175.0)
        }

        @DisplayName("빈 deltas 맵은 아무 작업도 수행하지 않는다")
        @Test
        fun `does nothing when deltas map is empty`() {
            // when
            productRankingWriter.incrementScores(testBucketKey, emptyMap())

            // then
            assertThat(redisTemplate.hasKey(testBucketKey)).isFalse()
        }

        @DisplayName("존재하지 않는 버킷에도 점수를 추가할 수 있다")
        @Test
        fun `creates bucket and adds scores when bucket does not exist`() {
            // given
            assertThat(redisTemplate.hasKey(testBucketKey)).isFalse()
            val deltas = mapOf(101L to Score.of(100.0))

            // when
            productRankingWriter.incrementScores(testBucketKey, deltas)

            // then
            assertThat(redisTemplate.hasKey(testBucketKey)).isTrue()
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(100.0)
        }
    }

    @DisplayName("replaceAll()")
    @Nested
    inner class ReplaceAll {

        @DisplayName("기존 점수를 모두 교체한다")
        @Test
        fun `replaces all existing scores`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)
            zSetOps.add(testBucketKey, "102", 200.0)

            val newScores = mapOf(
                103L to Score.of(500.0),
                104L to Score.of(600.0),
            )

            // when
            productRankingWriter.replaceAll(testBucketKey, newScores)

            // then - old entries should be removed
            val score101: Double? = zSetOps.score(testBucketKey, "101")
            val score102: Double? = zSetOps.score(testBucketKey, "102")
            assertThat(score101).isNull()
            assertThat(score102).isNull()

            // new entries should exist
            assertThat(zSetOps.score(testBucketKey, "103")).isEqualTo(500.0)
            assertThat(zSetOps.score(testBucketKey, "104")).isEqualTo(600.0)
        }

        @DisplayName("TTL이 올바르게 설정된다 (내부 TTL 2시간)")
        @Test
        fun `sets TTL correctly`() {
            // given
            val scores = mapOf(101L to Score.of(100.0))

            // when
            productRankingWriter.replaceAll(testBucketKey, scores)

            // then - 내부 TTL은 2시간(7200초)
            val ttl = redisTemplate.getExpire(testBucketKey, TimeUnit.SECONDS)
            assertThat(ttl).isGreaterThan(7100L)
            assertThat(ttl).isLessThanOrEqualTo(7200L)
        }

        @DisplayName("빈 scores 맵은 버킷을 삭제한다")
        @Test
        fun `deletes bucket when scores map is empty`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)
            assertThat(redisTemplate.hasKey(testBucketKey)).isTrue()

            // when
            productRankingWriter.replaceAll(testBucketKey, emptyMap())

            // then
            assertThat(redisTemplate.hasKey(testBucketKey)).isFalse()
        }
    }

    @DisplayName("createBucket()")
    @Nested
    inner class CreateBucket {

        @DisplayName("새 버킷을 생성하고 점수를 설정한다")
        @Test
        fun `creates new bucket with scores`() {
            // given
            val scores = mapOf(
                101L to Score.of(10.0),
                102L to Score.of(20.0),
            )

            // when
            productRankingWriter.createBucket(testBucketKey, scores)

            // then
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(10.0)
            assertThat(zSetOps.score(testBucketKey, "102")).isEqualTo(20.0)
        }

        @DisplayName("TTL이 올바르게 설정된다 (내부 TTL 2시간)")
        @Test
        fun `sets TTL correctly`() {
            // given
            val scores = mapOf(101L to Score.of(100.0))

            // when
            productRankingWriter.createBucket(testBucketKey, scores)

            // then - 내부 TTL은 2시간(7200초)
            val ttl = redisTemplate.getExpire(testBucketKey, TimeUnit.SECONDS)
            assertThat(ttl).isGreaterThan(7100L)
            assertThat(ttl).isLessThanOrEqualTo(7200L)
        }

        @DisplayName("빈 scores 맵은 버킷을 생성하지 않는다")
        @Test
        fun `does not create bucket when scores map is empty`() {
            // when
            productRankingWriter.createBucket(testBucketKey, emptyMap())

            // then
            assertThat(redisTemplate.hasKey(testBucketKey)).isFalse()
        }

        @DisplayName("기존 버킷이 있는 경우 점수가 추가된다")
        @Test
        fun `adds scores to existing bucket`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)
            val scores = mapOf(102L to Score.of(200.0))

            // when
            productRankingWriter.createBucket(testBucketKey, scores)

            // then
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(100.0)
            assertThat(zSetOps.score(testBucketKey, "102")).isEqualTo(200.0)
        }
    }

    @DisplayName("소수점 점수 처리")
    @Nested
    inner class DecimalScoreHandling {

        @DisplayName("BigDecimal 점수가 정확하게 저장된다")
        @Test
        fun `stores BigDecimal scores accurately`() {
            // given
            val deltas = mapOf(
                101L to Score.of(BigDecimal("123.45")),
                102L to Score.of(BigDecimal("0.01")),
            )

            // when
            productRankingWriter.incrementScores(testBucketKey, deltas)

            // then
            assertThat(zSetOps.score(testBucketKey, "101")).isEqualTo(123.45)
            assertThat(zSetOps.score(testBucketKey, "102")).isEqualTo(0.01)
        }
    }
}
