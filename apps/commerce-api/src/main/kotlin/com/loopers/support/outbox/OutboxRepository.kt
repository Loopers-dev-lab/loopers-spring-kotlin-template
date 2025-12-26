package com.loopers.support.outbox

/**
 * Outbox Repository 인터페이스
 *
 * - 메시지 저장 및 커서 기반 조회
 * - infrastructure/outbox에서 구현
 */
interface OutboxRepository {
    fun save(outbox: Outbox): Outbox
    fun findAllByIdGreaterThanOrderByIdAsc(cursorId: Long, limit: Int): List<Outbox>
}
