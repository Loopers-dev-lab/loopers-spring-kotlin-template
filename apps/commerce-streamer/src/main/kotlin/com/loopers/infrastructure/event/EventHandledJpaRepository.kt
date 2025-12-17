package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandled
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface EventHandledJpaRepository : JpaRepository<EventHandled, Long> {

    /**
     * 이벤트 ID로 처리 기록 존재 여부 확인
     */
    fun existsByEventId(eventId: String): Boolean

    /**
     * 특정 시각 이전에 처리된 이벤트 삭제 (클린업)
     */
    @Modifying
    @Query(
        """
        DELETE FROM EventHandled e
        WHERE e.handledAt < :handledBefore
        """,
    )
    fun deleteByHandledAtBefore(@Param("handledBefore") handledBefore: ZonedDateTime): Int
}
