package com.loopers.infrastructure.outbox

import com.loopers.support.outbox.Outbox
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OutboxJpaRepository : JpaRepository<Outbox, Long> {

    @Query("SELECT o FROM Outbox o WHERE o.id > :cursorId ORDER BY o.id ASC")
    fun findAllByIdGreaterThanOrderByIdAsc(
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<Outbox>
}
