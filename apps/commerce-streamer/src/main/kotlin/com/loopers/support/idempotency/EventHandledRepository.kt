package com.loopers.support.idempotency

/**
 * EventHandled Repository 인터페이스
 *
 * - 처리 완료 이벤트 저장 및 중복 체크
 * - infrastructure/idempotency에서 구현
 * - save/saveAll은 예외를 내부적으로 처리하고 IdempotencyResult를 반환
 */
interface EventHandledRepository {
    fun save(eventHandled: EventHandled): IdempotencyResult
    fun saveAll(eventHandledList: List<EventHandled>): IdempotencyResult
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
    fun findAllExistingKeys(idempotencyKeys: Set<String>): Set<String>
}
