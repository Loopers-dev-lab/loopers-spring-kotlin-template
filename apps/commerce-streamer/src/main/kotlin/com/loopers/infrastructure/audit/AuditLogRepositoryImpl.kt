package com.loopers.infrastructure.audit

import com.loopers.domain.audit.AuditLog
import com.loopers.domain.audit.AuditLogRepository
import org.springframework.stereotype.Repository

@Repository
class AuditLogRepositoryImpl(
    private val auditLogJpaRepository: AuditLogJpaRepository,
) : AuditLogRepository {

    override fun existsByEventId(eventId: String): Boolean {
        return auditLogJpaRepository.existsByEventId(eventId)
    }

    override fun save(auditLog: AuditLog): AuditLog {
        return auditLogJpaRepository.save(auditLog)
    }
}
