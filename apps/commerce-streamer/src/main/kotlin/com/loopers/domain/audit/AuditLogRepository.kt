package com.loopers.domain.audit

interface AuditLogRepository {
    fun existsByEventId(eventId: String): Boolean
    fun save(auditLog: AuditLog): AuditLog
}
