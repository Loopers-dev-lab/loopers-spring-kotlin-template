package com.loopers.support.outbox

/**
 * OutboxFailed Repository 인터페이스
 *
 * - 실패 메시지 저장 및 조회
 * - 재시도 가능한 메시지 조회
 * - infrastructure/outbox에서 구현
 */
interface OutboxFailedRepository {
    fun save(failed: OutboxFailed): OutboxFailed
    fun saveAll(failedList: List<OutboxFailed>): List<OutboxFailed>
    fun findRetryable(limit: Int): List<OutboxFailed>
    fun delete(failed: OutboxFailed)
    fun deleteAll(failedList: List<OutboxFailed>)
}
