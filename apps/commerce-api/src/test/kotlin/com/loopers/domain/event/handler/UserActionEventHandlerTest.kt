package com.loopers.domain.event.handler

import com.loopers.domain.event.UserActionEvent
import com.loopers.domain.event.UserActionType
import com.loopers.infrastructure.analytics.client.AnalyticsClient
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class UserActionEventHandlerTest {
    private val analyticsClient = mockk<AnalyticsClient>(relaxed = true)

    private val userActionEventHandler = UserActionEventHandler(analyticsClient)

    @DisplayName("유저 행동 이벤트를 처리할 때,")
    @Test
    fun handleUserAction_logsAction() {
        // given
        val event = UserActionEvent(
            userId = 1L,
            actionType = UserActionType.PRODUCT_VIEW,
            targetType = "PRODUCT",
            targetId = 100L,
            metadata = mapOf("source" to "homepage"),
            createdAt = ZonedDateTime.now(),
        )

        // when & then (예외가 발생하지 않아야 함)
        userActionEventHandler.handleUserAction(event)
    }

    @DisplayName("모든 유저 행동 타입에 대해 로깅이 가능하다")
    @Test
    fun handleUserAction_logsAllActionTypes() {
        // given & when & then
        UserActionType.values().forEach { actionType ->
            val event = UserActionEvent(
                userId = 1L,
                actionType = actionType,
                targetType = "TARGET",
                targetId = 100L,
                createdAt = ZonedDateTime.now(),
            )

            // 예외가 발생하지 않아야 함
            userActionEventHandler.handleUserAction(event)
        }
    }

    @DisplayName("메타데이터가 없어도 로깅이 가능하다")
    @Test
    fun handleUserAction_whenNoMetadata_thenLogsSuccessfully() {
        // given
        val event = UserActionEvent(
            userId = 1L,
            actionType = UserActionType.PRODUCT_LIKE,
            targetType = "PRODUCT",
            targetId = 100L,
            metadata = null,
            createdAt = ZonedDateTime.now(),
        )

        // when & then (예외가 발생하지 않아야 함)
        userActionEventHandler.handleUserAction(event)
    }

    @DisplayName("복잡한 메타데이터도 로깅이 가능하다")
    @Test
    fun handleUserAction_withComplexMetadata_thenLogsSuccessfully() {
        // given
        val event = UserActionEvent(
            userId = 1L,
            actionType = UserActionType.ORDER_CREATE,
            targetType = "ORDER",
            targetId = 100L,
            metadata = mapOf(
                "orderId" to 100L,
                "amount" to 50000,
                "paymentMethod" to "CARD",
                "couponId" to 200L,
            ),
            createdAt = ZonedDateTime.now(),
        )

        // when & then (예외가 발생하지 않아야 함)
        userActionEventHandler.handleUserAction(event)
    }
}
