package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxFailed
import com.loopers.support.outbox.OutboxFailedRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.Instant

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

    override fun findRetryable(limit: Int): List<OutboxFailed> {
        return outboxFailedJpaRepository.findRetryable(
            now = Instant.now(),
            pageable = PageRequest.of(0, limit),
        )
    }

    override fun delete(failed: OutboxFailed) {
        outboxFailedJpaRepository.delete(failed)
    }
}
