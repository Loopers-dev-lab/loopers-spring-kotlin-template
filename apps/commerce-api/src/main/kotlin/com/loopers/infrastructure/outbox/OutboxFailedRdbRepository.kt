package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import org.springframework.stereotype.Repository

@Repository
class OutboxFailedRdbRepository(
    private val outboxFailedJpaRepository: OutboxFailedJpaRepository,
) : OutboxFailedRepository {

    override fun save(failed: OutboxFailed): OutboxFailed {
        return outboxFailedJpaRepository.saveAndFlush(failed)
    }

    override fun saveAll(failedList: List<OutboxFailed>): List<OutboxFailed> {
        if (failedList.isEmpty()) return emptyList()
        return outboxFailedJpaRepository.saveAllAndFlush(failedList)
    }
}
