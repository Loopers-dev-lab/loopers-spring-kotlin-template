package com.loopers.infrastructure.audit

import com.loopers.domain.audit.AuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogJpaRepository : JpaRepository<AuditLog, Long> {
    fun existsByEventId(eventId: String): Boolean
}
