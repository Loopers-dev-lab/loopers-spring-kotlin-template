package com.loopers.support.outbox

/**
 * OutboxCursor Repository 인터페이스
 *
 * - 커서 저장 및 최신 커서 조회
 * - infrastructure/outbox에서 구현
 * - append-only: update 없이 insert만 수행
 */
interface OutboxCursorRepository {
    fun save(cursor: OutboxCursor): OutboxCursor
    fun findLatest(): OutboxCursor?
}
