package com.loopers.domain.metrics

import com.loopers.domain.event.like.ProductLikedEvent
import com.loopers.domain.event.like.ProductUnlikedEvent
import com.loopers.domain.product.event.ProductViewedEvent
import com.loopers.infrastructure.metrics.ProductMetricsRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * ProductMetricsService 단위 테스트
 *
 * 테스트 범위:
 * - 좋아요/조회수/판매량 증가/감소
 * - 이벤트 순서 보장 (occurredAt 기준)
 * - 낙관적 락 처리
 * - 배치 처리
 */
class ProductMetricsServiceTest {

    private lateinit var productMetricsRepository: ProductMetricsRepository
    private lateinit var productMetricsService: ProductMetricsService

    @BeforeEach
    fun setUp() {
        productMetricsRepository = mockk(relaxed = true)
        productMetricsService = ProductMetricsService(productMetricsRepository)
    }

    @DisplayName("좋아요 이벤트 처리 시 likesCount가 증가한다")
    @Test
    fun incrementLikes() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        val metrics = ProductMetrics(productId = productId, likesCount = 5)
        metrics.updatedAt = baseTime.minusSeconds(1) // 이벤트보다 과거로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics
        every { productMetricsRepository.save(any()) } answers { firstArg() }

        // when
        productMetricsService.incrementLikes(productId, baseTime)

        // then
        assertThat(metrics.likesCount).isEqualTo(6)
        verify(exactly = 1) { productMetricsRepository.save(metrics) }
    }

    @DisplayName("좋아요 취소 이벤트 처리 시 likesCount가 감소한다")
    @Test
    fun decrementLikes() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        val metrics = ProductMetrics(productId = productId, likesCount = 5)
        metrics.updatedAt = baseTime.minusSeconds(1) // 이벤트보다 과거로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics
        every { productMetricsRepository.save(any()) } answers { firstArg() }

        // when
        productMetricsService.decrementLikes(productId, baseTime)

        // then
        assertThat(metrics.likesCount).isEqualTo(4)
        verify(exactly = 1) { productMetricsRepository.save(metrics) }
    }

    @DisplayName("좋아요 취소 시 likesCount가 0이면 감소하지 않는다")
    @Test
    fun decrementLikesWhenZero() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        val metrics = ProductMetrics(productId = productId, likesCount = 0)
        metrics.updatedAt = baseTime.minusSeconds(1) // 이벤트보다 과거로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics
        every { productMetricsRepository.save(any()) } answers { firstArg() }

        // when
        productMetricsService.decrementLikes(productId, baseTime)

        // then
        assertThat(metrics.likesCount).isEqualTo(0)
        verify(exactly = 1) { productMetricsRepository.save(metrics) }
    }

    @DisplayName("조회 이벤트 처리 시 viewCount가 증가한다")
    @Test
    fun incrementViews() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        val metrics = ProductMetrics(productId = productId, viewCount = 10)
        metrics.updatedAt = baseTime.minusSeconds(1) // 이벤트보다 과거로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics
        every { productMetricsRepository.save(any()) } answers { firstArg() }

        // when
        productMetricsService.incrementViews(productId, baseTime)

        // then
        assertThat(metrics.viewCount).isEqualTo(11)
        verify(exactly = 1) { productMetricsRepository.save(metrics) }
    }

    @DisplayName("판매 이벤트 처리 시 salesCount가 증가한다")
    @Test
    fun incrementSales() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        val quantity = 3
        val metrics = ProductMetrics(productId = productId, salesCount = 10)
        metrics.updatedAt = baseTime.minusSeconds(1) // 이벤트보다 과거로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics
        every { productMetricsRepository.save(any()) } answers { firstArg() }

        // when
        productMetricsService.incrementSales(productId, baseTime, quantity)

        // then
        assertThat(metrics.salesCount).isEqualTo(13)
        verify(exactly = 1) { productMetricsRepository.save(metrics) }
    }

    @DisplayName("ProductMetrics가 없으면 새로 생성한다")
    @Test
    fun createNewMetricsWhenNotExists() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        val occurredAt = baseTime.plusSeconds(1) // ProductMetrics 생성 시간보다 미래로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns null
        every { productMetricsRepository.save(any<ProductMetrics>()) } answers { firstArg() }

        // when
        productMetricsService.incrementLikes(productId, occurredAt)

        // then
        verify(exactly = 1) { 
            productMetricsRepository.save(match<ProductMetrics> {
                it.productId == productId && it.likesCount == 1
            })
        }
    }

    @DisplayName("이벤트 순서 역전 시 무시한다 (occurredAt < updatedAt)")
    @Test
    fun ignoreOutOfOrderEvent() {
        // given
        val productId = 100L
        val oldTime = Instant.now().minusSeconds(10)
        val newTime = Instant.now()
        
        val metrics = ProductMetrics(productId = productId, likesCount = 5)
        metrics.updatedAt = newTime // 최신 시간으로 설정
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics

        // when: 과거 시간의 이벤트 처리 시도
        productMetricsService.incrementLikes(productId, oldTime)

        // then: 업데이트되지 않음
        assertThat(metrics.likesCount).isEqualTo(5)
        verify(exactly = 0) { productMetricsRepository.save(any()) }
    }

    @DisplayName("배치 이벤트 처리 시 시간순으로 정렬하여 처리한다")
    @Test
    fun processBatchEventsInOrder() {
        // given
        val productId = 100L
        val baseTime = Instant.now()
        
        val event1 = ProductLikedEvent(
            eventId = "event-1",
            aggregateId = productId,
            likeId = 1L,
            productId = productId,
            memberId = "user1",
            occurredAt = baseTime.plusSeconds(3)
        )
        val event2 = ProductViewedEvent(
            eventId = "event-2",
            aggregateId = productId,
            productId = productId,
            memberId = "user2",
            occurredAt = baseTime.plusSeconds(1)
        )
        val event3 = ProductLikedEvent(
            eventId = "event-3",
            aggregateId = productId,
            likeId = 2L,
            productId = productId,
            memberId = "user3",
            occurredAt = baseTime.plusSeconds(2)
        )
        
        val metrics = ProductMetrics(productId = productId)
        every { productMetricsRepository.findByProductId(productId) } returns metrics
        every { productMetricsRepository.save(any()) } answers { firstArg() }

        // when: 시간순이 아닌 순서로 전달
        productMetricsService.processBatchEvents(productId, listOf(event1, event2, event3))

        // then: 시간순으로 정렬되어 처리됨 (event2 (view) -> event3 (like) -> event1 (like))
        assertThat(metrics.likesCount).isEqualTo(2) // event3, event1
        assertThat(metrics.viewCount).isEqualTo(1) // event2
        verify(exactly = 1) { productMetricsRepository.save(metrics) }
    }

    @DisplayName("배치 이벤트 순서 역전 시 무시한다")
    @Test
    fun ignoreOutOfOrderBatchEvents() {
        // given
        val productId = 100L
        val oldTime = Instant.now().minusSeconds(10)
        val newTime = Instant.now()
        
        val metrics = ProductMetrics(productId = productId)
        metrics.updatedAt = newTime
        
        val oldEvent = ProductLikedEvent(
            eventId = "old-event",
            aggregateId = productId,
            likeId = 1L,
            productId = productId,
            memberId = "user1",
            occurredAt = oldTime
        )
        
        every { productMetricsRepository.findByProductId(productId) } returns metrics

        // when
        productMetricsService.processBatchEvents(productId, listOf(oldEvent))

        // then: 무시됨
        assertThat(metrics.likesCount).isEqualTo(0)
        verify(exactly = 0) { productMetricsRepository.save(any()) }
    }

}

