package com.loopers.domain.product

import com.loopers.fixtures.TestFixtures
import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestProduct
import com.loopers.testcontainers.RedisTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [RedisTestContainersConfig::class])
@Transactional
@DisplayName("ProductLikeCountService 테스트")
class ProductLikeCountServiceTest {

    @Autowired
    private lateinit var productLikeCountService: ProductLikeCountService

    @Autowired
    private lateinit var testFixtures: TestFixtures

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    private var productId: Long = 0

    @BeforeEach
    fun setUp() {
        testFixtures.clear()

        // Redis 모든 키 삭제
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()

        val brand = testFixtures.saveBrand(createTestBrand())
        val product = testFixtures.saveProduct(createTestProduct(brand = brand))
        productId = product.id
    }

    @Test
    @DisplayName("Redis 키가 없고 DB likeCount > 0일 때 첫 번째 decrement 요청이 정상적으로 처리되어야 한다")
    fun decrementShouldInitializeFromDatabaseWhenKeyIsMissing() {
        // given: DB에 likeCount가 3인 상태
        val product = testFixtures.saveProduct(
            createTestProduct(
                id = productId,
                brand = testFixtures.saveBrand(createTestBrand()),
            ),
        )
        product.setLikeCount(3L)

        // Redis 키가 없는 상태 확인
        val key = "product:like:count:$productId"
        assertThat(redisTemplate.hasKey(key)).isFalse()

        // when: 첫 번째 decrement 요청
        val result = productLikeCountService.decrement(productId)

        // then: DB의 초기값(3)에서 1을 뺀 2가 반환되어야 함
        assertThat(result).isEqualTo(2L)

        // Redis에 키가 생성되고 값이 2여야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(2L)
    }

    @Test
    @DisplayName("Redis 키가 없고 DB likeCount = 0일 때 decrement 요청은 0을 반환해야 한다")
    fun decrementShouldReturnZeroWhenDatabaseLikeCountIsZero() {
        // given: DB에 likeCount가 0인 상태
        val product = testFixtures.saveProduct(
            createTestProduct(
                id = productId,
                brand = testFixtures.saveBrand(createTestBrand()),
            ),
        )
        product.setLikeCount(0L)

        // Redis 키가 없는 상태 확인
        val key = "product:like:count:$productId"
        assertThat(redisTemplate.hasKey(key)).isFalse()

        // when: decrement 요청
        val result = productLikeCountService.decrement(productId)

        // then: 0이 반환되어야 함 (음수로 가지 않음)
        assertThat(result).isEqualTo(0L)

        // Redis에 키가 생성되고 값이 0이어야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(0L)
    }

    @Test
    @DisplayName("Redis 키가 존재할 때 decrement는 기존 값을 1 감소시켜야 한다")
    fun decrementShouldDecrementExistingRedisKey() {
        // given: Redis에 키가 5로 설정된 상태
        val key = "product:like:count:$productId"
        redisTemplate.opsForValue().set(key, "5")

        // when: decrement 요청
        val result = productLikeCountService.decrement(productId)

        // then: 4가 반환되어야 함
        assertThat(result).isEqualTo(4L)

        // Redis 값이 4여야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(4L)
    }

    @Test
    @DisplayName("Redis 키가 1일 때 decrement는 0으로 감소시켜야 한다")
    fun decrementShouldDecrementToZero() {
        // given: Redis에 키가 1로 설정된 상태
        val key = "product:like:count:$productId"
        redisTemplate.opsForValue().set(key, "1")

        // when: decrement 요청
        val result = productLikeCountService.decrement(productId)

        // then: 0이 반환되어야 함
        assertThat(result).isEqualTo(0L)

        // Redis 값이 0이어야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(0L)
    }

    @Test
    @DisplayName("Redis 키가 0일 때 decrement는 0을 유지해야 한다 (음수 방지)")
    fun decrementShouldNotGoBelowZero() {
        // given: Redis에 키가 0으로 설정된 상태
        val key = "product:like:count:$productId"
        redisTemplate.opsForValue().set(key, "0")

        // when: decrement 요청
        val result = productLikeCountService.decrement(productId)

        // then: 0이 반환되어야 함 (음수로 가지 않음)
        assertThat(result).isEqualTo(0L)

        // Redis 값이 여전히 0이어야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(0L)
    }

    @Test
    @DisplayName("increment는 Redis 키가 없을 때 DB에서 초기화하고 1 증가시켜야 한다")
    fun incrementShouldInitializeFromDatabaseWhenKeyIsMissing() {
        // given: DB에 likeCount가 2인 상태
        val product = testFixtures.saveProduct(
            createTestProduct(
                id = productId,
                brand = testFixtures.saveBrand(createTestBrand()),
            ),
        )
        product.setLikeCount(2L)

        // Redis 키가 없는 상태 확인
        val key = "product:like:count:$productId"
        assertThat(redisTemplate.hasKey(key)).isFalse()

        // when: increment 요청
        val result = productLikeCountService.increment(productId)

        // then: DB의 초기값(2)에서 1을 더한 3이 반환되어야 함
        assertThat(result).isEqualTo(3L)

        // Redis에 키가 생성되고 값이 3이어야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(3L)
    }

    @Test
    @DisplayName("increment는 Redis 키가 존재할 때 기존 값을 1 증가시켜야 한다")
    fun incrementShouldIncrementExistingRedisKey() {
        // given: Redis에 키가 5로 설정된 상태
        val key = "product:like:count:$productId"
        redisTemplate.opsForValue().set(key, "5")

        // when: increment 요청
        val result = productLikeCountService.increment(productId)

        // then: 6이 반환되어야 함
        assertThat(result).isEqualTo(6L)

        // Redis 값이 6이어야 함
        val redisValue = redisTemplate.opsForValue().get(key)?.toLongOrNull()
        assertThat(redisValue).isEqualTo(6L)
    }
}
