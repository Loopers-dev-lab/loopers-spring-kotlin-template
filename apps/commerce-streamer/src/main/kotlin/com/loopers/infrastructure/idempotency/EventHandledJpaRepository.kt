package com.loopers.infrastructure.idempotency

import com.loopers.support.idempotency.EventHandled
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EventHandledJpaRepository : JpaRepository<EventHandled, Long> {

    fun existsByIdempotencyKey(idempotencyKey: String): Boolean

    @Query("SELECT e.idempotencyKey FROM EventHandled e WHERE e.idempotencyKey IN :keys")
    fun findIdempotencyKeysByKeyIn(@Param("keys") keys: Set<String>): Set<String>
}
