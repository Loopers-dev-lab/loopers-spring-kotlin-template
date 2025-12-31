package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingReader
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

@SpringBootTest
@DisplayName("ProductRankingRedisReader 통합 테스트")
class ProductRankingRedisReaderIntegrationTest @Autowired constructor(
    private val productRankingReader: ProductRankingReader,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()
    private val testBucketKey = "ranking:products:2025011514"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("getAllScores()")
    @Nested
    inner class GetAllScores {

        @DisplayName("버킷 내 모든 상품의 점수를 조회한다")
        @Test
        fun `retrieves all product scores in bucket`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)
            zSetOps.add(testBucketKey, "102", 200.0)
            zSetOps.add(testBucketKey, "103", 300.0)

            // when
            val result = productRankingReader.getAllScores(testBucketKey)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[101L]).isEqualTo(Score.of(100.0))
            assertThat(result[102L]).isEqualTo(Score.of(200.0))
            assertThat(result[103L]).isEqualTo(Score.of(300.0))
        }

        @DisplayName("존재하지 않는 버킷 조회 시 빈 맵을 반환한다")
        @Test
        fun `returns empty map when bucket does not exist`() {
            // when
            val result = productRankingReader.getAllScores(testBucketKey)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("소수점 점수가 정확하게 조회된다")
        @Test
        fun `retrieves decimal scores accurately`() {
            // given
            zSetOps.add(testBucketKey, "101", 123.45)
            zSetOps.add(testBucketKey, "102", 0.01)

            // when
            val result = productRankingReader.getAllScores(testBucketKey)

            // then
            assertThat(result[101L]).isEqualTo(Score.of(123.45))
            assertThat(result[102L]).isEqualTo(Score.of(0.01))
        }

        @DisplayName("많은 수의 상품 점수를 조회한다")
        @Test
        fun `retrieves many product scores`() {
            // given
            (1L..100L).forEach { productId ->
                zSetOps.add(testBucketKey, productId.toString(), productId.toDouble())
            }

            // when
            val result = productRankingReader.getAllScores(testBucketKey)

            // then
            assertThat(result).hasSize(100)
            (1L..100L).forEach { productId ->
                assertThat(result[productId]).isEqualTo(Score.of(productId.toDouble()))
            }
        }
    }

    @DisplayName("exists()")
    @Nested
    inner class Exists {

        @DisplayName("버킷이 존재하면 true를 반환한다")
        @Test
        fun `returns true when bucket exists`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)

            // when
            val result = productRankingReader.exists(testBucketKey)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("버킷이 존재하지 않으면 false를 반환한다")
        @Test
        fun `returns false when bucket does not exist`() {
            // when
            val result = productRankingReader.exists(testBucketKey)

            // then
            assertThat(result).isFalse()
        }

        @DisplayName("빈 버킷이 삭제된 후 false를 반환한다")
        @Test
        fun `returns false after bucket is deleted`() {
            // given
            zSetOps.add(testBucketKey, "101", 100.0)
            assertThat(productRankingReader.exists(testBucketKey)).isTrue()

            // when
            redisTemplate.delete(testBucketKey)

            // then
            assertThat(productRankingReader.exists(testBucketKey)).isFalse()
        }
    }
}
