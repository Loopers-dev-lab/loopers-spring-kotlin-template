package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxEventJpaRepository : JpaRepository<OutboxEvent, Long> {

    /**
     * PENDING 상태의 이벤트를 생성 시각 순으로 조회
     */
    @Query(
        """
        SELECT e FROM OutboxEvent e
        WHERE e.status = :status
        ORDER BY e.createdAt ASC
        """,
    )
    fun findByStatusOrderByCreatedAtAsc(
        @Param("status") status: OutboxEventStatus,
        pageable: Pageable,
    ): List<OutboxEvent>

    /**
     * PENDING 상태의 이벤트를 비관적 락과 SKIP LOCKED로 조회
     * 동시에 실행되는 여러 스케줄러가 같은 이벤트를 가져가지 않도록 보장
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
        value = [
            QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"),
        ],
    )
    @Query(
        """
        SELECT e FROM OutboxEvent e
        WHERE e.status = :status
        ORDER BY e.createdAt ASC
        """,
    )
    fun findByStatusOrderByCreatedAtAscWithLock(
        @Param("status") status: OutboxEventStatus,
        pageable: Pageable,
    ): List<OutboxEvent>

    /**
     * 특정 시각 이전에 생성된 FAILED 상태 이벤트 조회
     */
    @Query(
        """
        SELECT e FROM OutboxEvent e
        WHERE e.status = :status
        AND e.createdAt < :createdBefore
        ORDER BY e.createdAt ASC
        """,
    )
    fun findByStatusAndCreatedAtBefore(
        @Param("status") status: OutboxEventStatus,
        @Param("createdBefore") createdBefore: LocalDateTime,
    ): List<OutboxEvent>

    /**
     * 특정 시각 이전에 발행된 이벤트 삭제 (클린업)
     */
    @Modifying
    @Query(
        """
        DELETE FROM OutboxEvent e
        WHERE e.status = :status
        AND e.publishedAt < :publishedBefore
        """,
    )
    fun deleteByStatusAndPublishedAtBefore(
        @Param("status") status: OutboxEventStatus,
        @Param("publishedBefore") publishedBefore: LocalDateTime,
    ): Int
}
