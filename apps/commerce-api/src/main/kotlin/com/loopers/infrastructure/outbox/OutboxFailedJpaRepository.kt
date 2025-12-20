package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.OutboxFailed
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OutboxFailedJpaRepository : JpaRepository<OutboxFailed, Long>
