package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OutboxJpaRepository : JpaRepository<Outbox, Long> {

    /**
     * 발행 대기 중인 이벤트 조회 (PENDING, FAILED 상태)
     * created_at 순서로 조회하여 이벤트 순서 보장
     */
    @Query(
        """
        SELECT o FROM Outbox o
        WHERE o.status IN :statuses
        ORDER BY o.createdAt ASC
        """,
    )
    fun findByStatusIn(statuses: List<OutboxStatus>, pageable: Pageable): List<Outbox>
}
