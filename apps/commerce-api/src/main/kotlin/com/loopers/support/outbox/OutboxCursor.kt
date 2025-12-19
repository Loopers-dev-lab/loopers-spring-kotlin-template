package com.loopers.support.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * OutboxCursor 엔티티 - 마지막 처리 위치 추적
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - Append-only: UPDATE 없이 INSERT만 수행
 * - 조회 시 ORDER BY created_at DESC LIMIT 1로 최신 커서
 */
@Entity
@Table(name = "outbox_cursor")
class OutboxCursor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "last_processed_id", nullable = false)
    val lastProcessedId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    companion object {
        fun create(lastProcessedId: Long): OutboxCursor {
            return OutboxCursor(
                lastProcessedId = lastProcessedId,
                createdAt = Instant.now(),
            )
        }
    }
}
