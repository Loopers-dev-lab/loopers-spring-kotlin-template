package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "dead_letter_queue",
    indexes = [
        Index(name = "idx_dlq_created_at", columnList = "createdAt"),
        Index(name = "idx_dlq_processed", columnList = "processed,createdAt")
    ]
)
class DeadLetterQueue(
    @Column(nullable = false, unique = true, length = 36)
    val eventId: String,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false)
    val aggregateId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val errorMessage: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val stackTrace: String,

    @Column(nullable = false)
    val originalRetryCount: Int,

    @Column(nullable = false)
    var processed: Boolean = false,

    var processedAt: Instant? = null,

    var resolvedBy: String? = null,

    @Column(columnDefinition = "TEXT")
    var resolution: String? = null
) : BaseEntity()
