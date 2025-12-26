package com.loopers.support.idempotency

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * EventHandledService - Consumer 멱등성 로직 캡슐화
 *
 * - Consumer와 Repository 사이의 중간 계층
 * - 저장 실패 시 예외를 전파하지 않고 로깅만 수행
 * - 비즈니스 로직 성공 후 멱등성 기록 실패로 전체 처리가 실패하는 것을 방지
 */
@Service
class EventHandledService(
    private val eventHandledRepository: EventHandledRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun isAlreadyHandled(idempotencyKey: String): Boolean {
        return eventHandledRepository.existsByIdempotencyKey(idempotencyKey)
    }

    fun markAsHandled(idempotencyKey: String) {
        runCatching {
            eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
        }.onFailure { e ->
            log.warn("Failed to save idempotency key: {}, duplicates may occur on retry", idempotencyKey, e)
        }
    }

    fun findAllExistingKeys(keys: Set<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        return eventHandledRepository.findAllExistingKeys(keys)
    }

    fun markAllAsHandled(idempotencyKeys: List<String>) {
        if (idempotencyKeys.isEmpty()) return
        runCatching {
            eventHandledRepository.saveAll(
                idempotencyKeys.map { EventHandled(idempotencyKey = it) },
            )
        }.onFailure { e ->
            log.warn("Failed to save {} idempotency keys, duplicates may occur on retry", idempotencyKeys.size, e)
        }
    }
}
