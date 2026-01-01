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
import java.util.concurrent.TimeUnit

@SpringBootTest
@DisplayName("ProductRankingRedisWriter 통합 테스트")
class ProductRankingRedisWriterIntegrationTest @Autowired constructor(
    private val productRankingWriter: ProductRankingWriter,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()
    private val testBucketKey = "ranking:products:2025011514"

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

        @DisplayName("100개를 초과하는 상품이 있을 경우 상위 100개만 유지한다")
        @Test
        fun `keeps only top 100 items when more than 100 are written`() {
            // given - 150개 상품 생성 (점수: 1.0 ~ 150.0)
            val scores = (1L..150L).associate { productId ->
                productId to Score.of(productId.toDouble())
            }

            // when
            productRankingWriter.replaceAll(testBucketKey, scores)

            // then - 버킷에는 100개만 존재해야 함
            val bucketSize = zSetOps.size(testBucketKey)
            assertThat(bucketSize).isEqualTo(100)

            // 상위 100개 (점수 51~150) 만 존재해야 함
            // 점수 1~50은 삭제되어야 함
            for (productId in 1L..50L) {
                val score: Double? = zSetOps.score(testBucketKey, productId.toString())
                assertThat(score).withFailMessage("Product $productId should be removed").isNull()
            }

            // 점수 51~150은 존재해야 함
            for (productId in 51L..150L) {
                val score: Double? = zSetOps.score(testBucketKey, productId.toString())
                assertThat(score).withFailMessage("Product $productId should exist with score ${productId.toDouble()}").isNotNull()
                assertThat(score).isEqualTo(productId.toDouble())
            }
        }
    }
}
