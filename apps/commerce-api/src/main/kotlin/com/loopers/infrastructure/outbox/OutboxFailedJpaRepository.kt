package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxFailed
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OutboxFailedJpaRepository : JpaRepository<OutboxFailed, Long> {

    @Query("SELECT f FROM OutboxFailed f WHERE f.nextRetryAt <= :now ORDER BY f.nextRetryAt ASC")
    fun findRetryable(
        @Param("now") now: Instant,
        pageable: Pageable,
    ): List<OutboxFailed>
}
