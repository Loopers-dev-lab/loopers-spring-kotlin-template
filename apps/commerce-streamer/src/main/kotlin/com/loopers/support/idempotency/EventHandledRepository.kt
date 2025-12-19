package com.loopers.support.idempotency

/**
 * EventHandled Repository 인터페이스
 *
 * - 처리 완료 이벤트 저장 및 중복 체크
 * - infrastructure/idempotency에서 구현
 */
interface EventHandledRepository {
    fun save(eventHandled: EventHandled): EventHandled
    fun saveAll(eventHandledList: List<EventHandled>): List<EventHandled>
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
    fun findAllExistingKeys(idempotencyKeys: Set<String>): Set<String>
}
