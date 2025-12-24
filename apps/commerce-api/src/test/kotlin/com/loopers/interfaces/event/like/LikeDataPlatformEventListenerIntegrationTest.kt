package com.loopers.interfaces.event.like

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.like.LikeDataPlatformClient
import com.loopers.domain.order.OrderDataPlatformClient
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LikeDataPlatformEventListener 통합 테스트
 *
 * 검증 범위:
 * - LikeCreatedEventV1 -> LikeDataPlatformClient.sendLikeCreated 호출 (AFTER_COMMIT, 비동기)
 * - LikeCanceledEventV1 -> LikeDataPlatformClient.sendLikeCanceled 호출 (AFTER_COMMIT, 비동기)
 *
 * 참고: DataPlatformClientAdapter는 현재 stub 구현이므로 실제 HTTP 호출이 없습니다.
 * 따라서 호출 여부를 상태로 검증하기 위해 AtomicBoolean 플래그를 사용합니다.
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

        val wasCalled = AtomicBoolean(false)
        every { likeDataPlatformClient.sendLikeCreated(userId, productId) } answers {
            wasCalled.set(true)
            true
        }

        // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
        transactionTemplate.execute {
            applicationEventPublisher.publishEvent(event)
        }

        // then - 비동기 처리이므로 Awaitility 사용, 상태 검증
        await().atMost(Duration.ofSeconds(5)).untilTrue(wasCalled)
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

        val wasCalled = AtomicBoolean(false)
        every { likeDataPlatformClient.sendLikeCreated(userId, productId) } answers {
            wasCalled.set(true)
            throw RuntimeException("External service error")
        }

        // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
        transactionTemplate.execute {
            applicationEventPublisher.publishEvent(event)
        }

        // then - 예외 발생에도 리스너가 실행됨을 상태로 검증
        // (리스너 내부에서 try-catch로 예외를 처리하므로 호출 완료됨)
        await().atMost(Duration.ofSeconds(5)).untilTrue(wasCalled)
    }

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

        val wasCalled = AtomicBoolean(false)
        every { likeDataPlatformClient.sendLikeCanceled(userId, productId) } answers {
            wasCalled.set(true)
            true
        }

        // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
        transactionTemplate.execute {
            applicationEventPublisher.publishEvent(event)
        }

        // then - 비동기 처리이므로 Awaitility 사용, 상태 검증
        await().atMost(Duration.ofSeconds(5)).untilTrue(wasCalled)
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

        val wasCalled = AtomicBoolean(false)
        every { likeDataPlatformClient.sendLikeCanceled(userId, productId) } answers {
            wasCalled.set(true)
            throw RuntimeException("External service error")
        }

        // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
        transactionTemplate.execute {
            applicationEventPublisher.publishEvent(event)
        }

        // then - 예외 발생에도 리스너가 실행됨을 상태로 검증
        // (리스너 내부에서 try-catch로 예외를 처리하므로 호출 완료됨)
        await().atMost(Duration.ofSeconds(5)).untilTrue(wasCalled)
    }
}
