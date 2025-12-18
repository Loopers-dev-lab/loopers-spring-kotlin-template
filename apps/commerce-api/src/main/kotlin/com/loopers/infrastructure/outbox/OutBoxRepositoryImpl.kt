package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutBoxModel
import com.loopers.domain.outbox.OutBoxRepository
import org.springframework.stereotype.Component

@Component
class OutBoxRepositoryImpl(private val outBoxJpaRepository: OutBoxJpaRepository) : OutBoxRepository {

    override fun save(outbox: OutBoxModel): OutBoxModel = outBoxJpaRepository.saveAndFlush(outbox)

    override fun findByEventId(eventId: String): OutBoxModel? = outBoxJpaRepository.findByEventId(eventId)
}
