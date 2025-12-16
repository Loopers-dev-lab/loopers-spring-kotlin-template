package com.loopers.interfaces.event.like

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.like.LikeDataPlatformClient
import com.loopers.domain.order.OrderDataPlatformClient
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

/**
 * LikeDataPlatformEventListener 통합 테스트
 *
 * 검증 범위:
 * - LikeCreatedEventV1 -> LikeDataPlatformClient.sendLikeCreated 호출 (AFTER_COMMIT, 비동기)
 * - LikeCanceledEventV1 -> LikeDataPlatformClient.sendLikeCanceled 호출 (AFTER_COMMIT, 비동기)
 */
@SpringBootTest
@DisplayName("LikeDataPlatformEventListener 통합 테스트")
class LikeDataPlatformEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @MockkBean
    private lateinit var likeDataPlatformClient: LikeDataPlatformClient

    @MockkBean
    private lateinit var orderDataPlatformClient: OrderDataPlatformClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("onLikeCreated")
    inner class OnLikeCreated {

        @Test
        @DisplayName("LikeCreatedEventV1 발행 시 LikeDataPlatformClient.sendLikeCreated가 호출된다")
        fun `LikeCreatedEventV1 triggers sendLikeCreated call`() {
            // given
            val userId = 1L
            val productId = 100L
            val event = LikeCreatedEventV1(
                userId = userId,
                productId = productId,
            )

            every { likeDataPlatformClient.sendLikeCreated(userId, productId) } returns true

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(exactly = 1) { likeDataPlatformClient.sendLikeCreated(userId, productId) }
            }
        }

        @Test
        @DisplayName("LikeCreatedEventV1 발행 시 sendLikeCreated가 실패해도 예외가 전파되지 않는다")
        fun `LikeCreatedEventV1 handles exception gracefully`() {
            // given
            val userId = 2L
            val productId = 200L
            val event = LikeCreatedEventV1(
                userId = userId,
                productId = productId,
            )

            every { likeDataPlatformClient.sendLikeCreated(userId, productId) } throws RuntimeException("External service error")

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용, 예외가 발생해도 호출은 됨
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(exactly = 1) { likeDataPlatformClient.sendLikeCreated(userId, productId) }
            }
        }
    }

    @Nested
    @DisplayName("onLikeCanceled")
    inner class OnLikeCanceled {

        @Test
        @DisplayName("LikeCanceledEventV1 발행 시 LikeDataPlatformClient.sendLikeCanceled가 호출된다")
        fun `LikeCanceledEventV1 triggers sendLikeCanceled call`() {
            // given
            val userId = 3L
            val productId = 300L
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            every { likeDataPlatformClient.sendLikeCanceled(userId, productId) } returns true

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(exactly = 1) { likeDataPlatformClient.sendLikeCanceled(userId, productId) }
            }
        }

        @Test
        @DisplayName("LikeCanceledEventV1 발행 시 sendLikeCanceled가 실패해도 예외가 전파되지 않는다")
        fun `LikeCanceledEventV1 handles exception gracefully`() {
            // given
            val userId = 4L
            val productId = 400L
            val event = LikeCanceledEventV1(
                userId = userId,
                productId = productId,
            )

            every { likeDataPlatformClient.sendLikeCanceled(userId, productId) } throws RuntimeException("External service error")

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용, 예외가 발생해도 호출은 됨
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(exactly = 1) { likeDataPlatformClient.sendLikeCanceled(userId, productId) }
            }
        }
    }
}
