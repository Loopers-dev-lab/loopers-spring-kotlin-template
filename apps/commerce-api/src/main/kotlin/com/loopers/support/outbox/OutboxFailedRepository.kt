package com.loopers.support.outbox

/**
 * OutboxFailed Repository 인터페이스
 *
 * - 실패 메시지 저장
 * - infrastructure/outbox에서 구현
 */
interface OutboxFailedRepository {
    fun save(failed: OutboxFailed): OutboxFailed
    fun saveAll(failedList: List<OutboxFailed>): List<OutboxFailed>
    fun findAll(): List<OutboxFailed>
}
