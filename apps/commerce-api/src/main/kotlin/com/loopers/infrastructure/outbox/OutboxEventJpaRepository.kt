package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface OutboxEventJpaRepository : JpaRepository<OutboxEvent, Long> {

    /**
     * PENDING 상태의 이벤트를 생성 시각 순으로 조회
     */
    @Query(
        """
        SELECT e FROM OutboxEvent e
        WHERE e.status = :status
        ORDER BY e.createdAt ASC
        LIMIT :limit
        """,
    )
    fun findByStatusOrderByCreatedAtAsc(
        @Param("status") status: OutboxEventStatus,
        @Param("limit") limit: Int,
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
        @Param("createdBefore") createdBefore: ZonedDateTime,
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
        @Param("publishedBefore") publishedBefore: ZonedDateTime,
    ): Int
}
