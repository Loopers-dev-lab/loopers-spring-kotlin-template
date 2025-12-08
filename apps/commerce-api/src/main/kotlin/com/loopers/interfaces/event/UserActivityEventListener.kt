package com.loopers.interfaces.event

import com.loopers.application.user.event.UserActivityEvent
import com.loopers.domain.integration.UserActivityPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 사용자 활동 이벤트를 처리하는 리스너
 *
 * 역할:
 * - 모든 사용자 활동(주문 생성, 주문 완료, 주문 실패 등)을 로깅
 * - 외부 로깅 시스템(Kafka, Elasticsearch, CloudWatch 등)으로 전송
 * - 사용자 행동 분석, 추천 시스템, 이상 탐지 등에 활용
 *
 * 특징:
 * - @EventListener: 트랜잭션과 무관하게 이벤트 발행 즉시 처리
 * - @Async: 비동기 처리로 메인 로직 성능에 영향 없음
 * - 로깅 실패해도 메인 비즈니스 로직에는 영향 없음 (Fire and Forget)
 */
@Component
class UserActivityEventListener(
    private val userActivityPublisher: UserActivityPublisher,
) {
    private val log = LoggerFactory.getLogger(UserActivityEventListener::class.java)

    /**
     * 사용자 활동 이벤트 처리 - 로깅 시스템 전송
     *
     * @EventListener: 트랜잭션 단계와 무관하게 이벤트 발행 즉시 처리
     * - TransactionalEventListener와 달리 트랜잭션 커밋 여부와 관계없이 동작
     * - 로깅은 즉시 수행되어야 하므로 @EventListener 사용
     *
     * @Async: 비동기 처리
     * - 로깅 작업이 메인 스레드를 블로킹하지 않음
     * - 외부 I/O가 느려도 응답 시간에 영향 없음
     */
    @Async
    @EventListener
    fun handleUserActivity(event: UserActivityEvent.UserActivity) {
        try {
            log.info(
                "사용자 활동 로깅: userId=${event.userId}, " +
                        "activityType=${event.activityType}, " +
                        "targetId=${event.targetId}, " +
                        "metadata=${event.metadata}, " +
                        "timestamp=${event.timestamp}",
            )

            // 외부 로깅 시스템으로 전송
            userActivityPublisher.sendUserActivity(event)

            log.debug("사용자 활동 로깅 완료: userId=${event.userId}, activityType=${event.activityType}")
        } catch (e: Exception) {
            // 로깅 실패는 메인 로직에 영향을 주지 않음
            // 중요한 이벤트의 경우 재시도 큐에 추가 고려
            log.error("사용자 활동 로깅 실패 (무시됨): userId=${event.userId}, activityType=${event.activityType}", e)
        }
    }
}
