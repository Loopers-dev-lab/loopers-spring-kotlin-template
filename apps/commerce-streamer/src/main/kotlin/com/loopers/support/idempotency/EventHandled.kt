package com.loopers.support.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * EventHandled 엔티티 - Consumer 멱등성 보장을 위한 처리 완료 이벤트 기록
 *
 * - BaseEntity를 상속하지 않음 (인프라 데이터)
 * - INSERT only (UPDATE 없음)
 * - idempotencyKey는 유니크 컬럼으로 중복 체크에 사용
 */
@Entity
@Table(name = "event_handled")
class EventHandled(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 500)
    val idempotencyKey: String,

    @Column(name = "handled_at", nullable = false, updatable = false)
    val handledAt: Instant = Instant.now(),
)
