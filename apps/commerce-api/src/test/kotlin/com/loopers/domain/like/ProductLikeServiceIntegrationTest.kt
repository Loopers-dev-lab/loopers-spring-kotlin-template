package com.loopers.domain.like

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents

/**
 * ProductLikeService 통합 테스트
 *
 * 검증 범위:
 * - 좋아요 추가/제거 동작
 * - 도메인 이벤트 발행 (LikeCreatedEventV1, LikeCanceledEventV1)
 */
@SpringBootTest
@RecordApplicationEvents
@DisplayName("ProductLikeService 통합 테스트")
class ProductLikeServiceIntegrationTest @Autowired constructor(
    private val productLikeService: ProductLikeService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("addLike")
    inner class AddLike {

        @Test
        @DisplayName("새로운 좋아요 추가 시 LikeCreatedEventV1을 발행한다")
        fun `publishes LikeCreatedEventV1 when adding new like`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            productLikeService.addLike(userId, productId)

            // then
            val events = applicationEvents.stream(LikeCreatedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.userId).isEqualTo(userId)
            assertThat(event.productId).isEqualTo(productId)
        }

        @Test
        @DisplayName("이미 존재하는 좋아요에 대해 이벤트를 발행하지 않는다")
        fun `does not publish event when like already exists`() {
            // given
            val userId = 1L
            val productId = 100L
            productLikeService.addLike(userId, productId)

            // Clear previously recorded events
            val eventsBeforeSecondCall = applicationEvents.stream(LikeCreatedEventV1::class.java).count()

            // when
            productLikeService.addLike(userId, productId)

            // then - Verify no additional events were published
            val eventsAfterSecondCall = applicationEvents.stream(LikeCreatedEventV1::class.java).count()
            assertThat(eventsAfterSecondCall).isEqualTo(eventsBeforeSecondCall)
        }
    }

    @Nested
    @DisplayName("removeLike")
    inner class RemoveLike {

        @Test
        @DisplayName("기존 좋아요 제거 시 LikeCanceledEventV1을 발행한다")
        fun `publishes LikeCanceledEventV1 when removing existing like`() {
            // given
            val userId = 1L
            val productId = 100L
            productLikeService.addLike(userId, productId)

            // when
            productLikeService.removeLike(userId, productId)

            // then
            val events = applicationEvents.stream(LikeCanceledEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.userId).isEqualTo(userId)
            assertThat(event.productId).isEqualTo(productId)
        }

        @Test
        @DisplayName("존재하지 않는 좋아요 제거 시 이벤트를 발행하지 않는다")
        fun `does not publish event when like does not exist`() {
            // given
            val userId = 1L
            val productId = 100L

            // when
            productLikeService.removeLike(userId, productId)

            // then
            val events = applicationEvents.stream(LikeCanceledEventV1::class.java).toList()
            assertThat(events).isEmpty()
        }
    }
}
